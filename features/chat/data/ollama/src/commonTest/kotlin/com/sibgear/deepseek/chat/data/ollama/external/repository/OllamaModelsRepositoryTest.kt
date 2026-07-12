package com.sibgear.deepseek.chat.data.ollama.external.repository

import com.sibgear.deepseek.chat.domain.model.AiProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class OllamaModelsRepositoryTest {
    @Test
    fun loadsOnlyModelsWithCompletionCapability() = runTest {
        var showRequestCount = 0
        val repository = OllamaModelsRepository(
            client = testClient { request ->
                when (request.url.encodedPath) {
                    "/api/tags" -> respondJson(
                        """
                            {
                              "models": [
                                {"name":"qwen3:8b","model":"qwen3:8b","details":{"family":"qwen3","parameter_size":"8B","quantization_level":"Q4_K_M"}},
                                {"name":"nomic-embed-text:latest","model":"nomic-embed-text:latest","details":{"family":"nomic-bert"}}
                              ]
                            }
                        """.trimIndent(),
                    )
                    "/api/show" -> {
                        showRequestCount += 1
                        if (showRequestCount == 1) {
                            respondJson(
                                """
                                    {
                                      "capabilities": ["completion"],
                                      "details": {"family":"qwen3","parameter_size":"8B","quantization_level":"Q4_K_M"},
                                      "model_info": {"qwen3.context_length": 40960}
                                    }
                                """.trimIndent(),
                            )
                        } else {
                            respondJson("""{"capabilities":["embedding"]}""")
                        }
                    }
                    else -> respond("", HttpStatusCode.NotFound)
                }
            },
        )

        val models = repository.loadModels()

        assertEquals(1, models.size)
        assertEquals("qwen3:8b", models.single().id)
        assertEquals(AiProvider.Ollama, models.single().provider)
        assertEquals(40960, models.single().contextLength)
        assertEquals(
            listOf(
                "temperature",
                "num_predict",
                "num_ctx",
                "top_p",
                "seed",
                "repeat_penalty",
                "stop",
            ),
            models.single().supportedParameters,
        )
    }
}

private fun testClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient =
    HttpClient(MockEngine { request -> handler(request) }) {
        install(ContentNegotiation) {
            json(ollamaJson())
        }
    }

private fun MockRequestHandleScope.respondJson(content: String): HttpResponseData =
    respond(
        content = content,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )
