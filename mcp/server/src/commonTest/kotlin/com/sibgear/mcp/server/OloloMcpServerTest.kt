package com.sibgear.mcp.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.sse.SSE
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking

class OloloMcpServerTest {

    @Test
    fun listsAndCallsOloloTool() = runBlocking {
        val port = 31337
        val logs = mutableListOf<String>()
        val engine = embeddedServer(ServerCIO, host = "127.0.0.1", port = port) {
            mcpStreamableHttp(path = "/mcp") {
                logMcpConnectionRequest(
                    context = this,
                    log = logs::add,
                    timestampProvider = { "2026-06-22T00:00:00Z" },
                )
                createOloloServer(logs::add)
            }
        }.start(wait = false)

        try {
            val url = "http://127.0.0.1:$port/mcp"

            HttpClient(ClientCIO) {
                install(SSE)
            }.use { httpClient ->
                val client = Client(
                    clientInfo = Implementation(
                        name = "ololo-test-client",
                        version = "1.0.0",
                    ),
                )
                val transport = StreamableHttpClientTransport(
                    client = httpClient,
                    url = url,
                )

                client.connect(transport)

                val tools = client.listTools().tools
                assertEquals(listOf("ololo"), tools.map { it.name })

                val result = client.callTool(name = "ololo", arguments = emptyMap())
                val textContent = assertIs<TextContent>(result.content.single())
                assertEquals("Hello OLOLO user!", textContent.text)

                assertEquals(
                    true,
                    logs.any {
                        it.contains("MCP client connection request:") &&
                            it.contains("timestamp=2026-06-22T00:00:00Z") &&
                            it.contains("method=POST") &&
                            it.contains("path=/mcp")
                    },
                )
                assertEquals(
                    true,
                    logs.any { it.startsWith("MCP client connected: activeSessions=") },
                )
            }
        } finally {
            engine.stop()
        }
    }
}
