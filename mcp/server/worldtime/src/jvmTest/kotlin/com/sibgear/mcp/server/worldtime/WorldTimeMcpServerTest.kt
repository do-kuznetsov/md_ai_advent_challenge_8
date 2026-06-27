package com.sibgear.mcp.server.worldtime

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
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WorldTimeMcpServerTest {
    @Test
    fun listsAndCallsWorldTimeTool() = runBlocking {
        withTestMcpClient { client ->
            val tools = client.listTools().tools
            assertEquals(listOf("get_world_time"), tools.map { it.name })

            val result = client.callTool(
                name = "get_world_time",
                arguments = mapOf("city" to "Novosibirsk"),
            )
            val textContent = assertIs<TextContent>(result.content.single())
            assertEquals("Время в Novosibirsk: 2026-06-27 19:00:00 (Asia/Novosibirsk)", textContent.text)
        }
    }

    @Test
    fun returnsUnavailableWorldTimeWhenClientFails() = runBlocking {
        withTestMcpClient(worldTimeResult = WorldTimeResult.Unavailable) { client ->
            val result = client.callTool(
                name = "get_world_time",
                arguments = mapOf("city" to "Unknown City"),
            )
            val textContent = assertIs<TextContent>(result.content.single())
            assertEquals("Время в Unknown City: недоступно", textContent.text)
        }
    }

    private suspend fun withTestMcpClient(
        worldTimeResult: WorldTimeResult = WorldTimeResult.Available(
            localDateTime = "2026-06-27 19:00:00",
            timezone = "Asia/Novosibirsk",
        ),
        block: suspend (Client) -> Unit,
    ) {
        val port = 31339
        val engine = embeddedServer(ServerCIO, host = "127.0.0.1", port = port) {
            mcpStreamableHttp(path = "/mcp") {
                createWorldTimeServer(
                    worldTimeClient = FakeWorldTimeClient(worldTimeResult),
                    log = {},
                )
            }
        }.start(wait = false)

        try {
            val url = "http://127.0.0.1:$port/mcp"

            HttpClient(ClientCIO) {
                install(SSE)
            }.use { httpClient ->
                val client = Client(
                    clientInfo = Implementation(
                        name = "worldtime-test-client",
                        version = "1.0.0",
                    ),
                )
                val transport = StreamableHttpClientTransport(
                    client = httpClient,
                    url = url,
                )

                client.connect(transport)
                block(client)
            }
        } finally {
            engine.stop()
        }
    }

    private class FakeWorldTimeClient(
        private val result: WorldTimeResult,
    ) : WorldTimeClient {
        override suspend fun getCurrentTime(city: String): WorldTimeResult = result
    }
}
