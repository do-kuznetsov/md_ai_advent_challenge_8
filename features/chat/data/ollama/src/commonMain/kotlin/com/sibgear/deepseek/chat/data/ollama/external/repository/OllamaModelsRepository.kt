package com.sibgear.deepseek.chat.data.ollama.external.repository

import com.sibgear.deepseek.chat.data.ollama.internal.model.OllamaApiErrorResponse
import com.sibgear.deepseek.chat.data.ollama.internal.model.OllamaShowRequest
import com.sibgear.deepseek.chat.data.ollama.internal.model.OllamaShowResponse
import com.sibgear.deepseek.chat.data.ollama.internal.model.OllamaTagsResponse
import com.sibgear.deepseek.chat.domain.model.AiModel
import com.sibgear.deepseek.chat.domain.model.AiProvider
import com.sibgear.deepseek.chat.domain.repository.AiModelsRepository
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

class OllamaModelsRepository(
    private val baseUrl: String = DefaultOllamaBaseUrl,
    private val client: HttpClient = defaultOllamaHttpClient(),
) : AiModelsRepository {
    private val json = ollamaJson()
    private val apiUrl = baseUrl.trimEnd('/')

    override suspend fun loadModels(): List<AiModel> {
        val response = client.get("$apiUrl/api/tags") {
            timeout {
                connectTimeoutMillis = ConnectTimeoutMillis
                socketTimeoutMillis = ModelsRequestTimeoutMillis
                requestTimeoutMillis = ModelsRequestTimeoutMillis
            }
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw IllegalStateException(formatApiError(response.status.value, response.status.description, body))
        }

        return json.decodeFromString<OllamaTagsResponse>(body)
            .models
            .mapNotNull { model ->
                val id = model.model?.takeIf { it.isNotBlank() }
                    ?: model.name?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val show = loadModelDetails(id) ?: return@mapNotNull null
                if ("completion" !in show.capabilities) {
                    return@mapNotNull null
                }
                val details = show.details ?: model.details
                AiModel(
                    id = id,
                    displayName = id,
                    provider = AiProvider.Ollama,
                    description = listOfNotNull(
                        details?.family,
                        details?.parameterSize,
                        details?.quantizationLevel,
                    ).joinToString(" / "),
                    contextLength = show.contextLength(),
                    supportedParameters = listOf(
                        "temperature",
                        "num_predict",
                        "num_ctx",
                        "top_p",
                        "seed",
                        "repeat_penalty",
                        "stop",
                    ),
                )
            }
    }

    private suspend fun loadModelDetails(model: String): OllamaShowResponse? {
        val response = client.post("$apiUrl/api/show") {
            contentType(ContentType.Application.Json)
            setBody(OllamaShowRequest(model = model))
            timeout {
                connectTimeoutMillis = ConnectTimeoutMillis
                socketTimeoutMillis = ModelsRequestTimeoutMillis
                requestTimeoutMillis = ModelsRequestTimeoutMillis
            }
        }
        if (!response.status.isSuccess()) {
            return null
        }
        return json.decodeFromString<OllamaShowResponse>(response.bodyAsText())
    }

    private fun OllamaShowResponse.contextLength(): Int? =
        modelInfo.entries
            .firstOrNull { it.key.endsWith(".context_length") }
            ?.value
            ?.jsonPrimitive
            ?.intOrNull

    private fun formatApiError(
        statusCode: Int,
        statusDescription: String,
        body: String,
    ): String {
        val apiMessage = runCatching {
            json.decodeFromString<OllamaApiErrorResponse>(body).error
        }.getOrNull()
        val message = apiMessage ?: body.take(600).ifBlank { "без тела ответа" }
        return "Ошибка Ollama API: HTTP $statusCode $statusDescription\n$message"
    }
}

internal const val DefaultOllamaBaseUrl = "http://localhost:11434"
internal const val ConnectTimeoutMillis = 10_000L
internal const val ModelsRequestTimeoutMillis = 60_000L

internal fun ollamaJson(): Json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

internal fun defaultOllamaHttpClient(): HttpClient =
    HttpClient {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = ConnectTimeoutMillis
            socketTimeoutMillis = DefaultRequestTimeoutMillis
            requestTimeoutMillis = DefaultRequestTimeoutMillis
        }
        install(ContentNegotiation) {
            json(ollamaJson())
        }
    }

internal const val DefaultRequestTimeoutMillis = 600_000L
