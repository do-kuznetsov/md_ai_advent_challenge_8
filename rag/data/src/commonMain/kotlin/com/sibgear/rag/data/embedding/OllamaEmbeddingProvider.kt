package com.sibgear.rag.data.embedding

import com.sibgear.rag.domain.repository.EmbeddingProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class OllamaEmbeddingProvider(
    private val model: String = "nomic-embed-text",
    private val baseUrl: String = "http://localhost:11434",
    private val httpClient: HttpClient = defaultHttpClient(),
) : EmbeddingProvider,
    AutoCloseable {
    override suspend fun embed(text: String): FloatArray {
        val response: OllamaEmbedResponse = httpClient.post("${baseUrl.trimEnd('/')}/api/embed") {
            contentType(ContentType.Application.Json)
            setBody(OllamaEmbedRequest(model = model, input = text))
        }.body()

        return response.embeddings
            .firstOrNull()
            ?.map(Double::toFloat)
            ?.toFloatArray()
            ?: error("Ollama returned empty embeddings response.")
    }

    override fun close() {
        httpClient.close()
    }

    private companion object {
        fun defaultHttpClient(): HttpClient =
            HttpClient(CIO) {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                        },
                    )
                }
            }
    }
}

@Serializable
internal data class OllamaEmbedRequest(
    val model: String,
    val input: String,
)

@Serializable
internal data class OllamaEmbedResponse(
    val embeddings: List<List<Double>> = emptyList(),
)
