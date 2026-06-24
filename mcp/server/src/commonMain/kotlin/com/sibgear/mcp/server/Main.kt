package com.sibgear.mcp.server

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ResourceUpdatedNotification
import io.modelcontextprotocol.kotlin.sdk.types.ResourceUpdatedNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import java.io.File

private const val DefaultHost = "127.0.0.1"
private const val DefaultPort = 3000
private const val DefaultDatabaseName = "visitor-log.db"
private const val McpPath = "/mcp"

fun main(args: Array<String>) {
    val log: (String) -> Unit = ::println
    val port = args.firstOrNull()?.toIntOrNull() ?: DefaultPort
    val databaseFile = args.getOrNull(1)?.takeIf { it != ":memory:" }?.let(::File)
        ?: defaultDatabaseFile()
    val database = args.getOrNull(1)?.let(::visitorLogRepositoryFromArgument)
        ?: JdbcVisitorLogRepository.file(databaseFile)
    val visitReportRepository = args.getOrNull(1)?.let(::visitReportRepositoryFromArgument)
        ?: JdbcVisitReportRepository.file(databaseFile)
    val weatherClient = OpenMeteoWeatherClient()
    val server = createVisitorLogServer(
        visitorLogRepository = database,
        visitReportRepository = visitReportRepository,
        weatherClient = weatherClient,
        log = log,
    )

    log("Starting Visitor Log MCP server at http://$DefaultHost:$port$McpPath")

    embeddedServer(CIO, host = DefaultHost, port = port) {
        mcpStreamableHttp(path = McpPath) {
            logMcpConnectionRequest(this, log)
            server
        }
    }.start(wait = true)
}

internal fun createVisitorLogServer(
    visitorLogRepository: VisitorLogRepository = JdbcVisitorLogRepository.file(defaultDatabaseFile()),
    visitReportRepository: VisitReportRepository = JdbcVisitReportRepository.file(defaultDatabaseFile()),
    weatherClient: WeatherClient = OpenMeteoWeatherClient(),
    visitReportSchedulerFactory: (
        (suspend (sessionId: String, resourceUri: String) -> Unit) -> VisitReportScheduler
    )? = null,
    log: (String) -> Unit = ::println,
): Server {
    val connectionLogger = McpConnectionLogger(log)
    lateinit var server: Server
    server = Server(
        serverInfo = Implementation(
            name = "visitor-log-server",
            version = "1.0.0",
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = false),
                resources = ServerCapabilities.Resources(
                    listChanged = false,
                    subscribe = true,
                ),
            ),
        ),
    )
    val notifyResourceUpdated: suspend (String, String) -> Unit = { sessionId, resourceUri ->
        server.sendResourceUpdated(
            sessionId = sessionId,
            notification = ResourceUpdatedNotification(
                ResourceUpdatedNotificationParams(uri = resourceUri),
            ),
        )
    }
    val scheduler = visitReportSchedulerFactory?.invoke(notifyResourceUpdated)
        ?: VisitReportScheduler(
            visitLogRepository = visitorLogRepository,
            reportRepository = visitReportRepository,
            notifyResourceUpdated = notifyResourceUpdated,
        )

    return server.apply {
        onConnect {
            connectionLogger.onConnect()
        }
        onClose {
            connectionLogger.onClose()
            scheduler.close()
        }
        registerVisitorLogTool(
            visitorLogRepository = visitorLogRepository,
            weatherClient = weatherClient,
        )
        registerScheduleVisitReportTool(scheduler)
        registerVisitReportResource(visitReportRepository)
        scheduler.start()
    }
}

private fun visitorLogRepositoryFromArgument(argument: String): VisitorLogRepository =
    if (argument == ":memory:") {
        JdbcVisitorLogRepository.inMemory()
    } else {
        JdbcVisitorLogRepository.file(File(argument))
    }

private fun visitReportRepositoryFromArgument(argument: String): VisitReportRepository =
    if (argument == ":memory:") {
        JdbcVisitReportRepository.inMemory()
    } else {
        JdbcVisitReportRepository.file(File(argument))
    }

internal fun defaultDatabaseFile(): File {
    val location = File(
        VisitorLogRepository::class.java.protectionDomain.codeSource.location.toURI(),
    )
    val directory = if (location.isFile) location.parentFile else location
    return File(directory, DefaultDatabaseName)
}
