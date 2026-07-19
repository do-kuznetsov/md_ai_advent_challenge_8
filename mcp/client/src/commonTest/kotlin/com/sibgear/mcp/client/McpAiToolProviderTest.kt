package com.sibgear.mcp.client

import com.sibgear.deepseek.chat.domain.model.AiToolInvocation
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.sse.SSE
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

class McpAiToolProviderTest {
    @Test
    fun disabledServersAreIgnored() = runTest {
        val provider = McpAiToolProvider(
            loadServers = {
                listOf(
                    McpServerConnection(
                        id = 1,
                        name = "local",
                        url = "http://127.0.0.1:1/mcp",
                        isEnabled = false,
                    ),
                )
            },
        )

        val catalog = provider.availableTools()

        assertEquals(emptyList(), catalog.tools)
        assertEquals(emptyList(), catalog.warnings)
    }

    @Test
    fun postOnlyFallbackDiscoversToolsWhenInitializedNotificationResponseIsInvalid() = runTest {
        val requests = mutableListOf<Int>()
        val provider = providerWithMockResponses { method, index ->
            requests += index
            when (method) {
                "initialize" -> initializeResponse()
                "notifications/initialized" -> invalidInitializedNotificationResponse()
                "tools/list" -> toolsListResponse()
                else -> error("Unexpected method $method")
            }
        }

        val catalog = provider.availableTools()

        assertEquals(emptyList(), catalog.warnings)
        assertEquals(listOf("time__get_local_time"), catalog.tools.map { it.name })
        assertEquals(true, requests.size >= 4)
    }

    @Test
    fun postOnlyFallbackCallsToolAndReturnsTextContent() = runTest {
        val provider = providerWithMockResponses { method, _ ->
            when (method) {
                "initialize" -> initializeResponse()
                "notifications/initialized" -> invalidInitializedNotificationResponse()
                "tools/list" -> toolsListResponse()
                "tools/call" -> toolCallResponse(
                    """
                    {
                      "jsonrpc": "2.0",
                      "id": 3,
                      "result": {
                        "content": [
                          {"type": "text", "text": "12:34 27-06-2026"}
                        ],
                        "isError": false
                      }
                    }
                    """.trimIndent(),
                )
                else -> error("Unexpected method $method")
            }
        }

        val result = provider.withSession { session ->
            session.callTool(
                AiToolInvocation(
                    name = "time__get_local_time",
                    arguments = JsonObject(emptyMap()),
                ),
            )
        }

        assertEquals("12:34 27-06-2026", result.content)
        assertEquals(false, result.isError)
    }

    @Test
    fun postOnlyFallbackConvertsJsonRpcErrorToToolError() = runTest {
        val provider = providerWithMockResponses { method, _ ->
            when (method) {
                "initialize" -> initializeResponse()
                "notifications/initialized" -> invalidInitializedNotificationResponse()
                "tools/list" -> toolsListResponse()
                "tools/call" -> toolCallResponse(
                    """
                    {
                      "jsonrpc": "2.0",
                      "id": 3,
                      "error": {
                        "code": -32603,
                        "message": "boom"
                      }
                    }
                    """.trimIndent(),
                )
                else -> error("Unexpected method $method")
            }
        }

        val result = provider.withSession { session ->
            session.callTool(
                AiToolInvocation(
                    name = "time__get_local_time",
                    arguments = JsonObject(emptyMap()),
                ),
            )
        }

        assertEquals(true, result.isError)
        assertEquals("Ошибка MCP tool 'time__get_local_time':\nboom", result.content)
    }

    @Test
    fun postOnlyFallbackReturnsUnknownContentBlocksAsJson() = runTest {
        val provider = providerWithMockResponses { method, _ ->
            when (method) {
                "initialize" -> initializeResponse()
                "notifications/initialized" -> invalidInitializedNotificationResponse()
                "tools/list" -> toolsListResponse()
                "tools/call" -> toolCallResponse(
                    """
                    {
                      "jsonrpc": "2.0",
                      "id": 3,
                      "result": {
                        "content": [
                          {"type": "image", "data": "abc", "mimeType": "image/png"}
                        ],
                        "isError": false
                      }
                    }
                    """.trimIndent(),
                )
                else -> error("Unexpected method $method")
            }
        }

        val result = provider.withSession { session ->
            session.callTool(
                AiToolInvocation(
                    name = "time__get_local_time",
                    arguments = JsonObject(emptyMap()),
                ),
            )
        }

        assertEquals("""{"type":"image","data":"abc","mimeType":"image/png"}""", result.content)
        assertEquals(false, result.isError)
    }

    @Test
    fun configuredHeadersAreSentToMcpRequests() = runTest {
        val capturedHeaders = mutableListOf<String?>()
        var requestIndex = 0
        val httpClient = HttpClient(
            MockEngine { request ->
                requestIndex += 1
                capturedHeaders += request.headers["X-Atlassian-Jira-Personal-Token"]
                val method = request.body.jsonRpcMethod()
                respond(
                    content = when (method) {
                        "initialize" -> initializeResponse()
                        "notifications/initialized" -> invalidInitializedNotificationResponse()
                        "tools/list" -> toolsListResponse()
                        else -> error("Unexpected method $method")
                    },
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
        ) {
            install(SSE)
        }
        val provider = McpAiToolProvider(
            loadServers = {
                listOf(
                    McpServerConnection(
                        id = 1,
                        name = "jira",
                        url = "https://ai-config.example/mcp",
                        isEnabled = true,
                        headers = mapOf("X-Atlassian-Jira-Personal-Token" to "secret-token"),
                    ),
                )
            },
            sessionFactory = DefaultMcpRemoteSessionFactory(
                httpClient = httpClient,
                primaryDiscoveryTimeout = 100.milliseconds,
            ),
        )

        val catalog = provider.availableTools()

        assertEquals(listOf("jira__get_local_time"), catalog.tools.map { it.name })
        assertEquals(true, capturedHeaders.isNotEmpty())
        assertEquals(true, capturedHeaders.all { it == "secret-token" })
    }

    @Test
    fun successfulFactorySessionIsUsedWithoutFallback() = runTest {
        val session = FakeRemoteSession(
            tools = listOf(
                RemoteMcpTool(
                    name = "lookup",
                    description = "Lookup",
                    inputSchema = JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("object"),
                        ),
                    ),
                ),
            ),
        )
        val factory = FakeRemoteSessionFactory(session)
        val provider = McpAiToolProvider(
            loadServers = {
                listOf(
                    McpServerConnection(
                        id = 1,
                        name = "primary",
                        url = "http://127.0.0.1:1/mcp",
                        isEnabled = true,
                    ),
                )
            },
            sessionFactory = factory,
        )

        val catalog = provider.availableTools()

        assertEquals(1, factory.connectCount)
        assertEquals(listOf("primary__lookup"), catalog.tools.map { it.name })
    }

    private fun providerWithMockResponses(
        responseBody: (method: String, requestIndex: Int) -> String,
    ): McpAiToolProvider {
        var requestIndex = 0
        val httpClient = HttpClient(
            MockEngine { request ->
                requestIndex += 1
                val method = request.body.jsonRpcMethod()
                respond(
                    content = responseBody(method, requestIndex),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
        ) {
            install(SSE)
        }
        return McpAiToolProvider(
            loadServers = {
                listOf(
                    McpServerConnection(
                        id = 1,
                        name = "time",
                        url = "https://ai-config.example/mcp",
                        isEnabled = true,
                    ),
                )
            },
            sessionFactory = DefaultMcpRemoteSessionFactory(
                httpClient = httpClient,
                primaryDiscoveryTimeout = 100.milliseconds,
            ),
        )
    }

    private fun initializeResponse(): String =
        """
        {
          "jsonrpc": "2.0",
          "id": 1,
          "result": {
            "capabilities": {"tools": {}},
            "protocolVersion": "2024-11-05",
            "serverInfo": {"name": "mcp-time", "version": "1.0.0"}
          }
        }
        """.trimIndent()

    private fun invalidInitializedNotificationResponse(): String =
        """
        {
          "jsonrpc": "2.0",
          "result": {
            "error": {
              "code": -32601,
              "message": "Unknown method"
            }
          }
        }
        """.trimIndent()

    private fun toolsListResponse(): String =
        """
        {
          "jsonrpc": "2.0",
          "id": 2,
          "result": {
            "tools": [
              {
                "name": "get_local_time",
                "description": "Текущее время сервера",
                "inputSchema": {
                  "type": "object",
                  "properties": {},
                  "required": []
                }
              }
            ]
          }
        }
        """.trimIndent()

    private fun toolCallResponse(body: String): String =
        body

    private fun Any.jsonRpcMethod(): String =
        ((this as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty())
            .substringAfter("\"method\":\"", missingDelimiterValue = "")
            .substringBefore("\"")
            .ifBlank {
                ((this as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty())
                    .substringAfter("\"method\": \"", missingDelimiterValue = "")
                    .substringBefore("\"")
            }
}

private class FakeRemoteSessionFactory(
    private val session: McpRemoteSession,
) : McpRemoteSessionFactory {
    var connectCount = 0
        private set

    override suspend fun connect(server: McpServerConnection): McpRemoteSession {
        connectCount += 1
        return session
    }
}

private class FakeRemoteSession(
    override val tools: List<RemoteMcpTool>,
) : McpRemoteSession {
    override suspend fun callTool(name: String, arguments: JsonObject): RemoteMcpToolResult =
        RemoteMcpToolResult(content = "ok", isError = false)

    override suspend fun close() = Unit
}
