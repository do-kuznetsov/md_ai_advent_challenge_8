package com.sibgear.deepseek.data.openrouter

import com.sibgear.deepseek.domain.AiModel
import com.sibgear.deepseek.domain.AiModelsRepository
import com.sibgear.deepseek.domain.AiProvider
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class OpenRouterModelsRepository(
    private val apiKey: String,
) : AiModelsRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val client = HttpClient {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = ConnectTimeoutMillis
            socketTimeoutMillis = ModelsRequestTimeoutMillis
            requestTimeoutMillis = ModelsRequestTimeoutMillis
        }
        install(ContentNegotiation) {
            json(json)
        }
    }

    override suspend fun loadModels(): List<AiModel> {
        val response = client.get(ModelsUrl) {
            bearerAuth(apiKey)
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

        return json.decodeFromString<OpenRouterModelsResponse>(body).data.mapNotNull { model ->
            model.id?.let { id ->
                AiModel(
                    id = id,
                    displayName = model.name?.takeIf { it.isNotBlank() } ?: id,
                    provider = AiProvider.OpenRouter,
                    description = model.description.orEmpty(),
                    contextLength = model.contextLength,
                    supportedParameters = model.supportedParameters.orEmpty(),
                )
            }
        }
    }

    private fun formatApiError(
        statusCode: Int,
        statusDescription: String,
        body: String,
    ): String {
        val apiMessage = runCatching {
            json.decodeFromString<ModelsApiErrorResponse>(body).error?.message
        }.getOrNull()

        val message = apiMessage ?: body.take(600).ifBlank { "без тела ответа" }
        return "Ошибка OpenRouter API: HTTP $statusCode $statusDescription\n$message"
    }
}

@Serializable
private data class OpenRouterModelsResponse(
    val data: List<OpenRouterModelDto> = emptyList(),
)

@Serializable
private data class OpenRouterModelDto(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    @SerialName("context_length")
    val contextLength: Int? = null,
    @SerialName("supported_parameters")
    val supportedParameters: List<String>? = null,
)

@Serializable
private data class ModelsApiErrorResponse(
    val error: ModelsApiError? = null,
)

@Serializable
private data class ModelsApiError(
    val message: String? = null,
)

private const val ModelsUrl = "https://openrouter.ai/api/v1/models"
private const val ConnectTimeoutMillis = 30_000L
private const val ModelsRequestTimeoutMillis = 60_000L
