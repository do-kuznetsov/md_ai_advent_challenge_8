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
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val DefaultHost = "127.0.0.1"
private const val DefaultPort = 3000
private const val McpPath = "/mcp"
private const val McpSessionIdHeader = "Mcp-Session-Id"

fun main(args: Array<String>) {
    val log: (String) -> Unit = ::println
    val port = args.firstOrNull()?.toIntOrNull() ?: DefaultPort
    val server = createOloloServer(log)

    log("Starting OLOLO MCP server at http://$DefaultHost:$port$McpPath")

    embeddedServer(CIO, host = DefaultHost, port = port) {
        mcpStreamableHttp(path = McpPath) {
            logMcpConnectionRequest(this, log)
            server
        }
    }.start(wait = true)
}

internal fun createOloloServer(log: (String) -> Unit = ::println): Server {
    val connectionLogger = McpConnectionLogger(log)

    return Server(
        serverInfo = Implementation(
            name = "ololo-server",
            version = "1.0.0",
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = false),
            ),
        ),
    ).apply {
        onConnect {
            connectionLogger.onConnect()
        }
        onClose {
            connectionLogger.onClose()
        }
        addTool(
            name = "ololo",
            description = "Returns a friendly OLOLO greeting.",
            inputSchema = ToolSchema(
                properties = buildJsonObject { },
                required = emptyList(),
            ),
        ) {
            CallToolResult(
                content = listOf(TextContent(text = "Hello OLOLO user!")),
            )
        }
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

@OptIn(ExperimentalTime::class)
private fun currentTimestamp(): String = Clock.System.now().toString()
