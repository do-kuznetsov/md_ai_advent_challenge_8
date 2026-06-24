package com.sibgear.mcp.server

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import java.io.File

private const val DefaultHost = "127.0.0.1"
private const val DefaultPort = 3000
private const val DefaultDatabaseName = "visitor-log.db"
private const val McpPath = "/mcp"

fun main(args: Array<String>) {
    val log: (String) -> Unit = ::println
    val port = args.firstOrNull()?.toIntOrNull() ?: DefaultPort
    val database = args.getOrNull(1)?.let(::visitorLogRepositoryFromArgument)
        ?: JdbcVisitorLogRepository.file(defaultDatabaseFile())
    val server = createVisitorLogServer(database, log)

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
    log: (String) -> Unit = ::println,
): Server {
    val connectionLogger = McpConnectionLogger(log)
    val server = Server(
        serverInfo = Implementation(
            name = "visitor-log-server",
            version = "1.0.0",
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = false),
            ),
        ),
    )

    return server.apply {
        onConnect {
            connectionLogger.onConnect()
        }
        onClose {
            connectionLogger.onClose()
        }
        registerVisitorLogTool(visitorLogRepository)
    }
}

private fun visitorLogRepositoryFromArgument(argument: String): VisitorLogRepository =
    if (argument == ":memory:") {
        JdbcVisitorLogRepository.inMemory()
    } else {
        JdbcVisitorLogRepository.file(File(argument))
    }

internal fun defaultDatabaseFile(): File {
    val location = File(
        VisitorLogRepository::class.java.protectionDomain.codeSource.location.toURI(),
    )
    val directory = if (location.isFile) location.parentFile else location
    return File(directory, DefaultDatabaseName)
}
