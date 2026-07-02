package com.sibgear.rag.data.embedding

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals

class OllamaEmbeddingProviderTest {
    @Test
    fun readsFirstEmbeddingFromApiEmbedResponse() = runTest {
        val client = HttpClient(
            MockEngine {
                respond(
                    content = """{"embeddings":[[1.0,2.5,-3.0]]}""",
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
        ) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val provider = OllamaEmbeddingProvider(
            model = "test-model",
            baseUrl = "http://localhost:11434",
            httpClient = client,
        )

        assertContentEquals(floatArrayOf(1f, 2.5f, -3f), provider.embed("hello"))
    }
}
