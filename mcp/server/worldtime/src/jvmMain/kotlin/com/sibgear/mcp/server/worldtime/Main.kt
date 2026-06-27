package com.sibgear.mcp.server.worldtime

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities

private const val DefaultHost = "127.0.0.1"
private const val DefaultPort = 3002
private const val McpPath = "/mcp"

fun main(args: Array<String>) {
    val log: (String) -> Unit = ::println
    val port = args.firstOrNull()?.toIntOrNull() ?: DefaultPort
    val server = createWorldTimeServer(
        worldTimeClient = OpenMeteoWorldTimeClient(),
        log = log,
    )

    log("Starting World Time MCP server at http://$DefaultHost:$port$McpPath")

    embeddedServer(CIO, host = DefaultHost, port = port) {
        mcpStreamableHttp(path = McpPath) {
            logMcpConnectionRequest(this, log)
            server
        }
    }.start(wait = true)
}

internal fun createWorldTimeServer(
    worldTimeClient: WorldTimeClient = OpenMeteoWorldTimeClient(),
    log: (String) -> Unit = ::println,
): Server {
    val connectionLogger = McpConnectionLogger(log)
    return Server(
        serverInfo = Implementation(
            name = "worldtime-server",
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
        registerWorldTimeTools(worldTimeClient)
    }
}
