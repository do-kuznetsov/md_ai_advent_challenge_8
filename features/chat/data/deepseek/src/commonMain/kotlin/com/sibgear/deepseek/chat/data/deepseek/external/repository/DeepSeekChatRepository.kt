package com.sibgear.deepseek.chat.data.deepseek.external.repository

import com.sibgear.deepseek.chat.data.deepseek.internal.mapper.toChatMessages
import com.sibgear.deepseek.chat.data.deepseek.internal.mapper.toDeepSeekAssistantHistoryMessage
import com.sibgear.deepseek.chat.data.deepseek.internal.mapper.toDeepSeekChatCompletionRequest
import com.sibgear.deepseek.chat.data.deepseek.internal.model.DeepSeekApiErrorResponse
import com.sibgear.deepseek.chat.data.deepseek.internal.model.DeepSeekChatCompletionResponse
import com.sibgear.deepseek.chat.domain.model.AgentResponse
import com.sibgear.deepseek.chat.domain.model.AiRequestData
import com.sibgear.deepseek.chat.domain.repository.AiChatRepository
import com.sibgear.deepseek.chat.history.domain.interactor.ChatHistoryInteractor
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessage
import com.sibgear.deepseek.chat.history.domain.model.HistoryRole
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
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlin.time.TimeSource

class DeepSeekChatRepository(
    private val apiKey: String,
    private val historyInteractor: ChatHistoryInteractor,
) : AiChatRepository {
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

    override suspend fun sendMessage(request: AiRequestData): AgentResponse {
        historyInteractor.add(HistoryMessage(role = HistoryRole.User, content = request.prompt))
        val startedAt = TimeSource.Monotonic.markNow()

        return try {
            val response = client.post(ChatCompletionsUrl) {
                bearerAuth(apiKey)
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(
                    request.toDeepSeekChatCompletionRequest(
                        historyMessages = historyInteractor.getMessages(),
                    ),
                )
            }

            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                return AgentResponse(
                    messages = historyInteractor.add(
                        request.toDeepSeekAssistantHistoryMessage(
                            content = formatApiError(
                                statusCode = response.status.value,
                                statusDescription = response.status.description,
                                body = body,
                            ),
                            responseTimeMs = startedAt.elapsedNow().inWholeMilliseconds,
                        ),
                    ).toChatMessages(),
                )
            }

            val completion = json.decodeFromString<DeepSeekChatCompletionResponse>(body)
            val assistantContent = completion.choices.firstOrNull()?.message?.content?.takeIf { it.isNotBlank() }
                ?: "DeepSeek вернул пустой ответ."

            AgentResponse(
                messages = historyInteractor.add(
                    request.toDeepSeekAssistantHistoryMessage(
                        content = assistantContent,
                        responseTimeMs = startedAt.elapsedNow().inWholeMilliseconds,
                        usage = completion.usage,
                    ),
                ).toChatMessages(),
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Throwable) {
            AgentResponse(
                messages = historyInteractor.add(
                    request.toDeepSeekAssistantHistoryMessage(
                        content = "Ошибка запроса: ${exception.message ?: exception::class.simpleName ?: "unknown"}",
                        responseTimeMs = startedAt.elapsedNow().inWholeMilliseconds,
                    ),
                ).toChatMessages(),
            )
        }
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

private const val ChatCompletionsUrl = "https://api.deepseek.com/chat/completions"
private const val ConnectTimeoutMillis = 30_000L
private const val RequestTimeoutMillis = 180_000L
