package com.sibgear.deepseek.chat.data.openrouter.external.repository

import com.sibgear.deepseek.chat.data.openrouter.internal.mapper.toChatMessages
import com.sibgear.deepseek.chat.data.openrouter.internal.mapper.toOpenRouterAssistantHistoryMessage
import com.sibgear.deepseek.chat.data.openrouter.internal.mapper.toOpenRouterChatCompletionRequest
import com.sibgear.deepseek.chat.data.openrouter.internal.mapper.toOpenRouterUserHistoryMessage
import com.sibgear.deepseek.chat.data.openrouter.internal.model.OpenRouterApiErrorResponse
import com.sibgear.deepseek.chat.data.openrouter.internal.model.OpenRouterCompletionResult
import com.sibgear.deepseek.chat.data.openrouter.internal.repository.OpenRouterMaxRetries
import com.sibgear.deepseek.chat.data.openrouter.internal.repository.OpenRouterRetryDelayMillis
import com.sibgear.deepseek.chat.data.openrouter.internal.repository.OpenRouterStreamAccumulator
import com.sibgear.deepseek.chat.data.openrouter.internal.repository.OpenRouterStreamResult
import com.sibgear.deepseek.chat.data.openrouter.internal.repository.isOpenRouterRetryableStatus
import com.sibgear.deepseek.chat.data.openrouter.internal.repository.isOpenRouterRetryableTransportError
import com.sibgear.deepseek.chat.data.openrouter.internal.repository.isTimeoutError
import com.sibgear.deepseek.chat.domain.model.AgentResponse
import com.sibgear.deepseek.chat.domain.model.AiRequestData
import com.sibgear.deepseek.chat.domain.repository.AiChatRepository
import com.sibgear.deepseek.chat.history.domain.interactor.ChatHistoryInteractor
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlin.time.TimeSource

class OpenRouterChatRepository(
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
            socketTimeoutMillis = DefaultRequestTimeoutMillis
            requestTimeoutMillis = DefaultRequestTimeoutMillis
        }
        install(ContentNegotiation) {
            json(json)
        }
    }

    override suspend fun sendMessage(request: AiRequestData): AgentResponse {
        historyInteractor.add(request.toOpenRouterUserHistoryMessage())
        val startedAt = TimeSource.Monotonic.markNow()
        var retryCount = 0

        return try {
            val completion = sendCompletionWithRetry(request) { retryCount = it }
            AgentResponse(
                messages = historyInteractor.add(
                    request.toOpenRouterAssistantHistoryMessage(
                        content = completion.content,
                        responseTimeMs = startedAt.elapsedNow().inWholeMilliseconds,
                        usage = completion.usage,
                        retryCount = retryCount,
                    ),
                ).toChatMessages(),
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Throwable) {
            AgentResponse(
                messages = historyInteractor.add(
                    request.toOpenRouterAssistantHistoryMessage(
                        content = formatException(exception),
                        responseTimeMs = startedAt.elapsedNow().inWholeMilliseconds,
                        retryCount = retryCount,
                    ),
                ).toChatMessages(),
            )
        }
    }

    private suspend fun sendCompletionWithRetry(
        request: AiRequestData,
        onRetryCountChanged: (Int) -> Unit,
    ): OpenRouterCompletionResult {
        var retryCount = 0

        while (true) {
            try {
                val completion = sendStreamingCompletion(request)
                if (completion.isRetryable && retryCount < OpenRouterMaxRetries) {
                    retryCount += 1
                    onRetryCountChanged(retryCount)
                    delay(OpenRouterRetryDelayMillis)
                    continue
                }

                return completion
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Throwable) {
                if (exception.isOpenRouterRetryableTransportError() && retryCount < OpenRouterMaxRetries) {
                    retryCount += 1
                    onRetryCountChanged(retryCount)
                    delay(OpenRouterRetryDelayMillis)
                    continue
                }

                throw exception
            }
        }
    }

    private suspend fun sendStreamingCompletion(request: AiRequestData): OpenRouterCompletionResult {
        val response = client.post(ChatCompletionsUrl) {
            bearerAuth(apiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            header(HttpHeaders.Accept, "text/event-stream")
            timeout {
                connectTimeoutMillis = ConnectTimeoutMillis
                socketTimeoutMillis = OpenRouterChatRequestTimeoutMillis
                requestTimeoutMillis = OpenRouterChatRequestTimeoutMillis
            }
            setBody(
                request.toOpenRouterChatCompletionRequest(
                    historyMessages = historyInteractor.getMessages(),
                ),
            )
        }

        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            return OpenRouterCompletionResult(
                content = formatApiError(
                    statusCode = response.status.value,
                    statusDescription = response.status.description,
                    body = body,
                ),
                isRetryable = response.status.isOpenRouterRetryableStatus(),
            )
        }

        val stream = response.readOpenRouterStream()
        val content = stream.content.takeIf { it.isNotBlank() }
            ?: "OpenRouter вернул пустой ответ."

        return OpenRouterCompletionResult(
            content = content,
            usage = stream.usage,
        )
    }

    private suspend fun HttpResponse.readOpenRouterStream(): OpenRouterStreamResult {
        val accumulator = OpenRouterStreamAccumulator(json)
        val channel = bodyAsChannel()

        while (true) {
            val line = channel.readLine() ?: break
            accumulator.acceptLine(line)
        }

        return accumulator.result()
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

private fun formatException(exception: Throwable): String {
    if (exception.isTimeoutError()) {
        return "OpenRouter timeout after ${OpenRouterChatRequestTimeoutMillis / 1_000}s"
    }

    return "Ошибка запроса: ${exception.message ?: exception::class.simpleName ?: "unknown"}"
}

private const val ChatCompletionsUrl = "https://openrouter.ai/api/v1/chat/completions"
private const val ConnectTimeoutMillis = 30_000L
private const val DefaultRequestTimeoutMillis = 180_000L
private const val OpenRouterChatRequestTimeoutMillis = 300_000L
