package com.sibgear.deepseek.chat.data.openrouter.external.service

import com.sibgear.deepseek.assistant.memory.domain.model.UserProfile
import com.sibgear.deepseek.assistant.memory.domain.service.AssistantProfileService
import com.sibgear.deepseek.chat.data.openrouter.internal.mapper.toUserProfile
import com.sibgear.deepseek.chat.data.openrouter.internal.model.OpenRouterApiChatMessage
import com.sibgear.deepseek.chat.data.openrouter.internal.model.OpenRouterApiErrorResponse
import com.sibgear.deepseek.chat.data.openrouter.internal.model.OpenRouterChatCompletionRequest
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class OpenRouterAssistantProfileService(
    private val apiKey: String,
) : AssistantProfileService {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val client = HttpClient {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = ConnectTimeoutMillis
            socketTimeoutMillis = RequestTimeoutMillis
            requestTimeoutMillis = RequestTimeoutMillis
        }
        install(ContentNegotiation) {
            json(json)
        }
    }

    override suspend fun updateProfile(
        currentProfile: UserProfile,
        interviewAnswers: List<String>,
        modelId: String,
    ): UserProfile {
        val response = client.post(ChatCompletionsUrl) {
            bearerAuth(apiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                OpenRouterChatCompletionRequest(
                    model = modelId,
                    messages = listOf(
                        OpenRouterApiChatMessage(role = "user", content = profileUpdatePrompt(currentProfile, interviewAnswers)),
                    ),
                    stream = false,
                ),
            )
        }

        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error(formatApiError(response.status.value, response.status.description, body))
        }

        val content = json.decodeFromString<OpenRouterProfileResponse>(body)
            .choices
            .firstOrNull()
            ?.message
            ?.content
            ?.takeIf { it.isNotBlank() }
            ?: error("OpenRouter вернул пустой профиль.")

        return content.toUserProfile(json)
            ?: error("OpenRouter вернул профиль в неожиданном формате.")
    }

    private fun formatApiError(
        statusCode: Int,
        statusDescription: String,
        body: String,
    ): String {
        val apiMessage = runCatching {
            json.decodeFromString<OpenRouterApiErrorResponse>(body).error?.message
        }.getOrNull()

        val message = apiMessage ?: body.take(600).ifBlank { "без тела ответа" }
        return "Ошибка OpenRouter API: HTTP $statusCode $statusDescription\n$message"
    }
}

private fun profileUpdatePrompt(
    currentProfile: UserProfile,
    interviewAnswers: List<String>,
): String =
    buildString {
        appendLine("Ты обновляешь профиль пользователя для персонализации ассистента.")
        appendLine("Верни только JSON object без markdown: {\"profile\":\"...\"}.")
        appendLine()
        appendLine("Профиль должен быть кратким свободным текстом на русском.")
        appendLine("Сохрани предпочтения по стилю, формату, ограничениям и рабочему контексту.")
        appendLine("Не выдумывай факты и не добавляй ничего, чего нет в ответах.")
        appendLine()
        appendLine("Текущий профиль:")
        appendLine(currentProfile.text.ifBlank { "пусто" })
        appendLine()
        appendLine("Ответы интервью:")
        interviewAnswers.forEachIndexed { index, answer ->
            appendLine("${index + 1}. ${answer.ifBlank { "нет ответа" }}")
        }
    }

@Serializable
private data class OpenRouterProfileResponse(
    val choices: List<OpenRouterProfileChoice> = emptyList(),
)

@Serializable
private data class OpenRouterProfileChoice(
    val message: OpenRouterProfileMessage? = null,
)

@Serializable
private data class OpenRouterProfileMessage(
    val content: String? = null,
)

private const val ChatCompletionsUrl = "https://openrouter.ai/api/v1/chat/completions"
private const val ConnectTimeoutMillis = 30_000L
private const val RequestTimeoutMillis = 180_000L
