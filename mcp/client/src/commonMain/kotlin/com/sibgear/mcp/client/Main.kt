package com.sibgear.mcp.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.runBlocking

private const val DefaultMcpUrl = "http://127.0.0.1:3000/mcp"

fun main(args: Array<String>) = runBlocking {
    val url = args.firstOrNull() ?: DefaultMcpUrl

    println("Starting Visitor Log MCP client")
    println("Target MCP URL: $url")

    HttpClient(CIO) {
        install(SSE)
    }.use { httpClient ->
        println("Initializing MCP client")
        val client = Client(
            clientInfo = Implementation(
                name = "visitor-log-cli-client",
                version = "1.0.0",
            ),
        )
        val transport = StreamableHttpClientTransport(
            client = httpClient,
            url = url,
        )

        try {
            println("Connecting to MCP server")
            client.connect(transport)
            println("MCP connection established")

            println("Requesting available tools")
            val tools = client.listTools().tools

            println("Found ${tools.size} tool(s)")
            tools.forEach { tool ->
                val description = tool.description?.takeIf { it.isNotBlank() } ?: "no description"
                println("- ${tool.name}: $description")
            }

            println("MCP client finished successfully")
        } catch (error: Throwable) {
            println("MCP client failed: ${error.message}")
            throw error
        }
    }
}
