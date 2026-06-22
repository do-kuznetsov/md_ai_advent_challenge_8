package com.sibgear.mcp.server

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject

private const val DefaultHost = "127.0.0.1"
private const val DefaultPort = 3000
private const val McpPath = "/mcp"

fun main(args: Array<String>) {
    val port = args.firstOrNull()?.toIntOrNull() ?: DefaultPort
    val server = createOloloServer()

    println("Starting OLOLO MCP server at http://$DefaultHost:$port$McpPath")

    embeddedServer(CIO, host = DefaultHost, port = port) {
        mcpStreamableHttp(path = McpPath) {
            server
        }
    }.start(wait = true)
}

internal fun createOloloServer(): Server =
    Server(
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
