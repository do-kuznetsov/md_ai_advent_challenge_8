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
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class VisitorLogMcpServerTest {

    @Test
    fun listsAndCallsVisitorLogTool() = runBlocking {
        withTestMcpClient { client, logs ->
            val tools = client.listTools().tools
            assertEquals(listOf("visitor_log"), tools.map { it.name })

            val result = client.callTool(
                name = "visitor_log",
                arguments = mapOf(
                    "userName" to "Dmitry",
                    "localTime" to "2026-06-24 20:00",
                    "city" to "Novosibirsk",
                    "limit" to 1,
                ),
            )
            val textContent = assertIs<TextContent>(result.content.single())
            assertEquals(
                "Dmitry из Novosibirsk заходил в 2026-06-24 20:00 через visitor-log-test-client/1.0.0",
                textContent.text,
            )

            assertTrue(
                logs.any {
                    it.contains("MCP client connection request:") &&
                        it.contains("timestamp=2026-06-22T00:00:00Z") &&
                        it.contains("method=POST") &&
                        it.contains("path=/mcp")
                },
            )
            assertTrue(logs.any { it.startsWith("MCP client connected: activeSessions=") })
        }
    }

    @Test
    fun returnsLatestVisitsNewestFirst() = runBlocking {
        withTestMcpClient { client, _ ->
            client.callTool(
                name = "visitor_log",
                arguments = mapOf(
                    "userName" to "Anna",
                    "localTime" to "2026-06-24 19:00",
                    "city" to "Tomsk",
                    "limit" to 1,
                ),
            )
            val result = client.callTool(
                name = "visitor_log",
                arguments = mapOf(
                    "userName" to "Boris",
                    "localTime" to "2026-06-24 20:30",
                    "city" to "Omsk",
                    "limit" to 2,
                ),
            )
            val textContent = assertIs<TextContent>(result.content.single())

            assertEquals(
                listOf(
                    "Boris из Omsk заходил в 2026-06-24 20:30 через visitor-log-test-client/1.0.0",
                    "Anna из Tomsk заходил в 2026-06-24 19:00 через visitor-log-test-client/1.0.0",
                ),
                textContent.text.lines(),
            )
        }
    }

    @Test
    fun limitZeroStoresVisitAndReturnsEmptyText() = runBlocking {
        withTestMcpClient { client, _ ->
            val emptyResult = client.callTool(
                name = "visitor_log",
                arguments = mapOf(
                    "userName" to "Elena",
                    "localTime" to "2026-06-24 21:00",
                    "city" to "Barnaul",
                    "limit" to 0,
                ),
            )
            val emptyTextContent = assertIs<TextContent>(emptyResult.content.single())
            assertEquals("", emptyTextContent.text)

            val latestResult = client.callTool(
                name = "visitor_log",
                arguments = mapOf(
                    "userName" to "Fedor",
                    "localTime" to "2026-06-24 21:05",
                    "city" to "Kemerovo",
                    "limit" to 2,
                ),
            )
            val latestTextContent = assertIs<TextContent>(latestResult.content.single())
            assertEquals(
                listOf(
                    "Fedor из Kemerovo заходил в 2026-06-24 21:05 через visitor-log-test-client/1.0.0",
                    "Elena из Barnaul заходил в 2026-06-24 21:00 через visitor-log-test-client/1.0.0",
                ),
                latestTextContent.text.lines(),
            )
        }
    }

    private suspend fun withTestMcpClient(
        block: suspend (Client, List<String>) -> Unit,
    ) {
        val port = 31337
        val logs = mutableListOf<String>()
        val repository = JdbcVisitorLogRepository.inMemory()
        val engine = embeddedServer(ServerCIO, host = "127.0.0.1", port = port) {
            mcpStreamableHttp(path = "/mcp") {
                logMcpConnectionRequest(
                    context = this,
                    log = logs::add,
                    timestampProvider = { "2026-06-22T00:00:00Z" },
                )
                createVisitorLogServer(
                    visitorLogRepository = repository,
                    log = logs::add,
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
                        name = "visitor-log-test-client",
                        version = "1.0.0",
                    ),
                )
                val transport = StreamableHttpClientTransport(
                    client = httpClient,
                    url = url,
                )

                client.connect(transport)
                block(client, logs)
            }
        } finally {
            engine.stop()
            repository.close()
        }
    }
}
