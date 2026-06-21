package com.sibgear.deepseek.chat.data.deepseek.external.service

import com.sibgear.deepseek.assistant.memory.domain.model.UserProfile
import com.sibgear.deepseek.assistant.memory.domain.service.AssistantProfileService
import com.sibgear.deepseek.chat.data.deepseek.internal.mapper.toUserProfile
import com.sibgear.deepseek.chat.data.deepseek.internal.model.DeepSeekApiChatMessage
import com.sibgear.deepseek.chat.data.deepseek.internal.model.DeepSeekApiErrorResponse
import com.sibgear.deepseek.chat.data.deepseek.internal.model.DeepSeekChatCompletionRequest
import com.sibgear.deepseek.chat.data.deepseek.internal.model.DeepSeekChatCompletionResponse
import com.sibgear.deepseek.chat.data.deepseek.internal.model.DeepSeekThinking
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
import kotlinx.serialization.json.Json

class DeepSeekAssistantProfileService(
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
                DeepSeekChatCompletionRequest(
                    model = modelId,
                    messages = listOf(
                        DeepSeekApiChatMessage(role = "user", content = profileUpdatePrompt(currentProfile, interviewAnswers)),
                    ),
                    stream = false,
                    thinking = DeepSeekThinking(type = "disabled"),
                ),
            )
        }

        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error(formatApiError(response.status.value, response.status.description, body))
        }

        val content = json.decodeFromString<DeepSeekChatCompletionResponse>(body)
            .choices
            .firstOrNull()
            ?.message
            ?.content
            ?.takeIf { it.isNotBlank() }
            ?: error("DeepSeek вернул пустой профиль.")

        return content.toUserProfile(json)
            ?: error("DeepSeek вернул профиль в неожиданном формате.")
    }

    private fun formatApiError(
        statusCode: Int,
        statusDescription: String,
        body: String,
    ): String {
        val apiMessage = runCatching {
            json.decodeFromString<DeepSeekApiErrorResponse>(body).error?.message
        }.getOrNull()

        val message = apiMessage ?: body.take(600).ifBlank { "без тела ответа" }
        return "Ошибка DeepSeek API: HTTP $statusCode $statusDescription\n$message"
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
        appendLine("Если пользователь указал другой язык общения, зафиксируй это как предпочтение в профиле.")
        appendLine("Сохрани только устойчивые факты для персонализации код-ассистента:")
        appendLine("- язык общения и форму обращения;")
        appendLine("- желаемую краткость или подробность ответов;")
        appendLine("- стиль общения и раздражающие паттерны;")
        appendLine("- роль пользователя, контекст проекта, продукт, аудиторию и текущую цель;")
        appendLine("- технические рамки: стек, архитектуру, сторонние зависимости, кодстайл, тесты, безопасность и процесс.")
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

private const val ChatCompletionsUrl = "https://api.deepseek.com/chat/completions"
private const val ConnectTimeoutMillis = 30_000L
private const val RequestTimeoutMillis = 180_000L
