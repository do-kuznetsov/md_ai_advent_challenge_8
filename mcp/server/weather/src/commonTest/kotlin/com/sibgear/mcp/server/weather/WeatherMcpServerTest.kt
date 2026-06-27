package com.sibgear.mcp.server.weather

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

class WeatherMcpServerTest {
    @Test
    fun listsAndCallsWeatherTool() = runBlocking {
        withTestMcpClient { client ->
            val tools = client.listTools().tools
            assertEquals(listOf("get_weather"), tools.map { it.name })

            val result = client.callTool(
                name = "get_weather",
                arguments = mapOf("city" to "Novosibirsk"),
            )
            val textContent = assertIs<TextContent>(result.content.single())
            assertEquals("Погода в Novosibirsk: 18.4 °C", textContent.text)
        }
    }

    @Test
    fun returnsUnavailableWeatherWhenWeatherClientFails() = runBlocking {
        withTestMcpClient(weatherResult = WeatherResult.Unavailable) { client ->
            val result = client.callTool(
                name = "get_weather",
                arguments = mapOf("city" to "Unknown City"),
            )
            val textContent = assertIs<TextContent>(result.content.single())
            assertEquals("Погода в Unknown City: недоступна", textContent.text)
        }
    }

    private suspend fun withTestMcpClient(
        weatherResult: WeatherResult = WeatherResult.Available(18.4),
        block: suspend (Client) -> Unit,
    ) {
        val port = 31338
        val engine = embeddedServer(ServerCIO, host = "127.0.0.1", port = port) {
            mcpStreamableHttp(path = "/mcp") {
                createWeatherServer(
                    weatherClient = FakeWeatherClient(weatherResult),
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
                        name = "weather-test-client",
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

    private class FakeWeatherClient(
        private val result: WeatherResult,
    ) : WeatherClient {
        override suspend fun getTemperature(city: String): WeatherResult = result
    }
}
