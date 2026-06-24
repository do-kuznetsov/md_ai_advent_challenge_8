package com.sibgear.mcp.server

import io.ktor.http.HttpHeaders
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.origin
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.routing.RoutingContext
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val DefaultHost = "127.0.0.1"
private const val DefaultPort = 3000
private const val DefaultDatabaseName = "visitor-log.db"
private const val MaxVisitLimit = 100
private const val McpPath = "/mcp"
private const val McpSessionIdHeader = "Mcp-Session-Id"
private const val VisitorLogToolName = "visitor_log"

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
        addTool(
            name = VisitorLogToolName,
            description = "Stores the current visitor and returns recent visit log entries.",
            inputSchema = visitorLogToolSchema(),
        ) { request ->
            val arguments = request.arguments
                ?: throw IllegalArgumentException("Missing visitor_log arguments")
            val input = VisitorLogInput(
                userName = arguments.requiredString("userName"),
                localTime = arguments.requiredString("localTime"),
                city = arguments.requiredString("city"),
                limit = arguments.requiredInt("limit"),
            )
            val clientInfo = server.sessions[sessionId]?.clientVersion
            val visit = VisitorLogEntry(
                userName = input.userName,
                localTime = input.localTime,
                city = input.city,
                clientName = clientInfo?.name ?: "unknown",
                clientVersion = clientInfo?.version ?: "unknown",
                createdAt = currentTimestamp(),
            )
            val recentVisits = visitorLogRepository.addAndGetRecent(
                entry = visit,
                limit = input.limit.coerceIn(0, MaxVisitLimit),
            )

            CallToolResult(
                content = listOf(TextContent(text = recentVisits.joinToString("\n") { it.formatForResponse() })),
            )
        }
    }
}

internal data class VisitorLogInput(
    val userName: String,
    val localTime: String,
    val city: String,
    val limit: Int,
)

internal data class VisitorLogEntry(
    val userName: String,
    val localTime: String,
    val city: String,
    val clientName: String,
    val clientVersion: String,
    val createdAt: String,
) {
    fun formatForResponse(): String =
        "$userName из $city заходил в $localTime через $clientName/$clientVersion"
}

internal interface VisitorLogRepository {
    fun addAndGetRecent(entry: VisitorLogEntry, limit: Int): List<VisitorLogEntry>
}

internal class JdbcVisitorLogRepository private constructor(
    private val jdbcUrl: String,
) : VisitorLogRepository, AutoCloseable {
    private val connection: Connection = DriverManager.getConnection(jdbcUrl)

    init {
        connection.createStatement().use { statement ->
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS visitor_log(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_name TEXT NOT NULL,
                    local_time TEXT NOT NULL,
                    city TEXT NOT NULL,
                    client_name TEXT NOT NULL,
                    client_version TEXT NOT NULL,
                    created_at TEXT NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    override fun addAndGetRecent(entry: VisitorLogEntry, limit: Int): List<VisitorLogEntry> =
        synchronized(connection) {
            connection.prepareStatement(
                """
                INSERT INTO visitor_log(
                    user_name,
                    local_time,
                    city,
                    client_name,
                    client_version,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, entry.userName)
                statement.setString(2, entry.localTime)
                statement.setString(3, entry.city)
                statement.setString(4, entry.clientName)
                statement.setString(5, entry.clientVersion)
                statement.setString(6, entry.createdAt)
                statement.executeUpdate()
            }

            if (limit <= 0) {
                emptyList()
            } else {
                connection.prepareStatement(
                    """
                    SELECT user_name, local_time, city, client_name, client_version, created_at
                    FROM visitor_log
                    ORDER BY id DESC
                    LIMIT ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setInt(1, limit)
                    statement.executeQuery().use { resultSet ->
                        buildList {
                            while (resultSet.next()) {
                                add(resultSet.toVisitorLogEntry())
                            }
                        }
                    }
                }
            }
        }

    override fun close() {
        connection.close()
    }

    private fun ResultSet.toVisitorLogEntry(): VisitorLogEntry =
        VisitorLogEntry(
            userName = getString("user_name"),
            localTime = getString("local_time"),
            city = getString("city"),
            clientName = getString("client_name"),
            clientVersion = getString("client_version"),
            createdAt = getString("created_at"),
        )

    companion object {
        fun file(databaseFile: File): JdbcVisitorLogRepository {
            databaseFile.parentFile?.mkdirs()
            return JdbcVisitorLogRepository("jdbc:sqlite:${databaseFile.absolutePath}")
        }

        fun inMemory(): JdbcVisitorLogRepository =
            JdbcVisitorLogRepository("jdbc:sqlite::memory:")
    }
}

private class McpConnectionLogger(
    private val log: (String) -> Unit,
) {
    private var isConnected = false

    fun onConnect() {
        if (isConnected) return

        isConnected = true
        log("MCP client connected: activeSessions=1")
    }

    fun onClose() {
        if (!isConnected) return

        isConnected = false
        log("MCP client disconnected: activeSessions=0")
    }
}

internal fun logMcpConnectionRequest(
    context: RoutingContext,
    log: (String) -> Unit = ::println,
    timestampProvider: () -> String = ::currentTimestamp,
) {
    val request = context.call.request
    val remoteHost = request.origin.remoteHost
    val userAgent = request.header(HttpHeaders.UserAgent) ?: "unknown"
    val mcpSessionId = request.header(McpSessionIdHeader) ?: "none"

    log(
        "MCP client connection request: " +
            "timestamp=${timestampProvider()} " +
            "remote=$remoteHost " +
            "method=${request.httpMethod.value} " +
            "path=${request.path()} " +
            "userAgent=$userAgent " +
            "mcpSessionId=$mcpSessionId",
    )
}

private fun visitorLogToolSchema(): ToolSchema =
    ToolSchema(
        properties = buildJsonObject {
            put("userName", stringProperty("Visitor name."))
            put("localTime", stringProperty("Visitor local time."))
            put("city", stringProperty("Visitor city."))
            put(
                "limit",
                buildJsonObject {
                    put("type", "integer")
                    put("description", "How many recent visits to return.")
                    put("minimum", 0)
                    put("maximum", MaxVisitLimit)
                },
            )
        },
        required = listOf("userName", "localTime", "city", "limit"),
    )

private fun stringProperty(description: String): JsonObject =
    buildJsonObject {
        put("type", "string")
        put("description", description)
    }

private fun JsonObject.requiredString(key: String): String =
    requiredPrimitive(key).contentOrNull?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("Missing or blank '$key'")

private fun JsonObject.requiredInt(key: String): Int =
    requiredPrimitive(key).intOrNull
        ?: throw IllegalArgumentException("Missing or invalid integer '$key'")

private fun JsonObject.requiredPrimitive(key: String): JsonPrimitive =
    get(key)?.jsonPrimitive
        ?: throw IllegalArgumentException("Missing '$key'")

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

@OptIn(ExperimentalTime::class)
private fun currentTimestamp(): String = Clock.System.now().toString()
