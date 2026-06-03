package com.sibgear.deepseek.data

import com.sibgear.deepseek.domain.AgentResponse
import com.sibgear.deepseek.domain.ApiSettings
import com.sibgear.deepseek.domain.ChatMessage
import com.sibgear.deepseek.domain.ChatRole
import com.sibgear.deepseek.domain.DeepSeekRepository
import com.sibgear.deepseek.domain.DeepSeekRequestData
import io.ktor.client.HttpClient
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
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class KtorDeepSeekRepository(
    private val history: InMemoryChatHistory = InMemoryChatHistory(),
) : DeepSeekRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val client = HttpClient {
        expectSuccess = false
        install(ContentNegotiation) {
            json(json)
        }
    }

    override suspend fun sendMessage(request: DeepSeekRequestData): AgentResponse {
        history.add(ChatMessage(role = ChatRole.User, content = request.prompt))

        return try {
            val response = client.post("https://api.deepseek.com/chat/completions") {
                bearerAuth(request.apiKey)
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(
                    ChatCompletionRequest(
                        model = request.model.id,
                        messages = request.apiMessages(history.getMessages()),
                        stream = false,
                        thinking = Thinking(type = "disabled"),
                        temperature = request.apiSettings.deepSeekTemperature(),
                        maxTokens = request.apiSettings.deepSeekMaxTokens(),
                        stop = request.apiSettings.deepSeekStop(),
                    ),
                )
            }

            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                val errorContent = formatApiError(
                        statusCode = response.status.value,
                        statusDescription = response.status.description,
                        body = body,
                    )

                return AgentResponse(
                    messages = history.add(ChatMessage(role = ChatRole.Assistant, content = errorContent)),
                )
            }

            val completion = json.decodeFromString<ChatCompletionResponse>(body)
            val assistantContent = completion.choices.firstOrNull()?.message?.content?.takeIf { it.isNotBlank() }
                ?: "DeepSeek вернул пустой ответ."

            AgentResponse(
                messages = history.add(ChatMessage(role = ChatRole.Assistant, content = assistantContent)),
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Throwable) {
            val errorContent = "Ошибка запроса: ${exception.message ?: exception::class.simpleName ?: "unknown"}"
            AgentResponse(
                messages = history.add(ChatMessage(role = ChatRole.Assistant, content = errorContent)),
            )
        }
    }

    private fun formatApiError(statusCode: Int, statusDescription: String, body: String): String {
        val apiMessage = runCatching {
            json.decodeFromString<DeepSeekErrorResponse>(body).error?.message
        }.getOrNull()

        val message = apiMessage ?: body.take(600).ifBlank { "без тела ответа" }
        return "Ошибка API: HTTP $statusCode $statusDescription\n$message"
    }
}

private fun DeepSeekRequestData.apiMessages(historyMessages: List<ChatMessage>): List<ApiChatMessage> {
    val trimmedSystemPrompt = systemPrompt.trim()
    return buildList {
        if (trimmedSystemPrompt.isNotEmpty()) {
            add(ApiChatMessage(role = "system", content = trimmedSystemPrompt))
        }

        historyMessages.forEach { message ->
            add(ApiChatMessage(role = message.role.apiRole, content = message.content))
        }
    }
}

private val ChatRole.apiRole: String
    get() = when (this) {
        ChatRole.User -> "user"
        ChatRole.Assistant -> "assistant"
    }

private fun ApiSettings.deepSeekTemperature(): Float? {
    if (!isApiControlEnabled) {
        return null
    }

    return temperature.coerceIn(0f, 1f) * 2f
}

private fun ApiSettings.deepSeekMaxTokens(): Int? {
    if (!isApiControlEnabled || maxTokens <= 0) {
        return null
    }

    return maxTokens
}

private fun ApiSettings.deepSeekStop(): List<String>? {
    if (!isApiControlEnabled) {
        return null
    }

    val trimmedStopWord = stopWord.trim()
    return trimmedStopWord.takeIf { it.isNotEmpty() }?.let { listOf(it) }
}

@Serializable
private data class ChatCompletionRequest(
    val model: String,
    val messages: List<ApiChatMessage>,
    val stream: Boolean,
    val thinking: Thinking,
    val temperature: Float? = null,
    @kotlinx.serialization.SerialName("max_tokens")
    val maxTokens: Int? = null,
    val stop: List<String>? = null,
)

@Serializable
private data class ApiChatMessage(
    val role: String,
    val content: String,
)

@Serializable
private data class Thinking(
    val type: String,
)

@Serializable
private data class ChatCompletionResponse(
    val choices: List<Choice> = emptyList(),
)

@Serializable
private data class Choice(
    val message: AssistantMessage? = null,
)

@Serializable
private data class AssistantMessage(
    val content: String? = null,
)

@Serializable
private data class DeepSeekErrorResponse(
    val error: DeepSeekError? = null,
)

@Serializable
private data class DeepSeekError(
    val message: String? = null,
)
