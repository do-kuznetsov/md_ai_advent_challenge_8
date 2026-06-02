package com.sibgear.deepseek.data

import com.sibgear.deepseek.domain.AgentResponse
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

class KtorDeepSeekRepository : DeepSeekRepository {
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
        return try {
            val response = client.post("https://api.deepseek.com/chat/completions") {
                bearerAuth(request.apiKey)
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(
                    ChatCompletionRequest(
                        model = request.model.id,
                        messages = listOf(ChatMessage(role = "user", content = request.prompt)),
                        stream = false,
                        thinking = Thinking(type = "disabled"),
                    ),
                )
            }

            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                return AgentResponse(
                    content = formatApiError(
                        statusCode = response.status.value,
                        statusDescription = response.status.description,
                        body = body,
                    ),
                )
            }

            val completion = json.decodeFromString<ChatCompletionResponse>(body)
            AgentResponse(
                content = completion.choices.firstOrNull()?.message?.content?.takeIf { it.isNotBlank() }
                    ?: "DeepSeek вернул пустой ответ.",
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Throwable) {
            AgentResponse(
                content = "Ошибка запроса: ${exception.message ?: exception::class.simpleName ?: "unknown"}",
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

@Serializable
private data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean,
    val thinking: Thinking,
)

@Serializable
private data class ChatMessage(
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
