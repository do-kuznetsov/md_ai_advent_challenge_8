package com.sibgear.deepseek.data.openrouter

import com.sibgear.deepseek.domain.AgentResponse
import com.sibgear.deepseek.domain.AiChatRepository
import com.sibgear.deepseek.domain.AiRequestData
import com.sibgear.deepseek.domain.ApiSettings
import com.sibgear.deepseek.domain.ChatMessage
import com.sibgear.deepseek.domain.ChatMessageFooter
import com.sibgear.deepseek.domain.ChatRole
import com.sibgear.deepseek.history.domain.ChatHistoryInteractor
import com.sibgear.deepseek.history.domain.HistoryMessage
import com.sibgear.deepseek.history.domain.HistoryMessageFooter
import com.sibgear.deepseek.history.domain.HistoryRole
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
        historyInteractor.add(HistoryMessage(role = HistoryRole.User, content = request.prompt))
        val startedAt = TimeSource.Monotonic.markNow()
        var retryCount = 0

        return try {
            val completion = sendCompletionWithRetry(request) { retryCount = it }
            AgentResponse(
                messages = historyInteractor.add(
                    request.assistantHistoryMessage(
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
                    request.assistantHistoryMessage(
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
    ): CompletionResult {
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

    private suspend fun sendStreamingCompletion(request: AiRequestData): CompletionResult {
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
                request.chatCompletionRequest(
                    historyMessages = historyInteractor.getMessages(),
                ),
            )
        }

        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            return CompletionResult(
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

        return CompletionResult(
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
            json.decodeFromString<ApiErrorResponse>(body).error?.message
        }.getOrNull()

        val message = apiMessage ?: body.take(600).ifBlank { "без тела ответа" }
        return "Ошибка OpenRouter API: HTTP $statusCode $statusDescription\n$message"
    }
}

private data class CompletionResult(
    val content: String,
    val usage: OpenRouterResponseUsage? = null,
    val isRetryable: Boolean = false,
)

private fun formatException(exception: Throwable): String {
    if (exception.isTimeoutError()) {
        return "OpenRouter timeout after ${OpenRouterChatRequestTimeoutMillis / 1_000}s"
    }

    return "Ошибка запроса: ${exception.message ?: exception::class.simpleName ?: "unknown"}"
}

private fun AiRequestData.chatCompletionRequest(historyMessages: List<HistoryMessage>): ChatCompletionRequest {
    val trimmedSystemPrompt = systemPrompt.trim()
    return ChatCompletionRequest(
        model = model.id,
        messages = buildList {
            if (trimmedSystemPrompt.isNotEmpty()) {
                add(ApiChatMessage(role = "system", content = trimmedSystemPrompt))
            }

            historyMessages.forEach { message ->
                add(ApiChatMessage(role = message.role.apiRole, content = message.content))
            }
        },
        stream = true,
        temperature = apiSettings.openRouterTemperature(),
        maxTokens = apiSettings.openRouterMaxTokens(),
        stop = apiSettings.openRouterStop(),
    )
}

private fun AiRequestData.assistantHistoryMessage(
    content: String,
    responseTimeMs: Long,
    usage: OpenRouterResponseUsage? = null,
    retryCount: Int = 0,
): HistoryMessage =
    HistoryMessage(
        role = HistoryRole.Assistant,
        content = content,
        sourceLabel = "OpenRouter / ${model.displayName}",
        footer = HistoryMessageFooter(
            responseTimeMs = responseTimeMs,
            promptTokens = usage?.promptTokens,
            completionTokens = usage?.completionTokens,
            totalTokens = usage?.displayTotalTokens,
            cost = usage?.cost,
            retryCount = retryCount,
        ),
    )

private fun List<HistoryMessage>.toChatMessages(): List<ChatMessage> =
    map { it.toChatMessage() }

private fun HistoryMessage.toChatMessage(): ChatMessage =
    ChatMessage(
        role = role.toChatRole(),
        content = content,
        sourceLabel = sourceLabel,
        footer = footer?.toChatMessageFooter(),
    )

private fun HistoryRole.toChatRole(): ChatRole =
    when (this) {
        HistoryRole.User -> ChatRole.User
        HistoryRole.Assistant -> ChatRole.Assistant
    }

private fun HistoryMessageFooter.toChatMessageFooter(): ChatMessageFooter =
    ChatMessageFooter(
        responseTimeMs = responseTimeMs,
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        totalTokens = totalTokens,
        cost = cost,
        retryCount = retryCount,
    )

private val HistoryRole.apiRole: String
    get() = when (this) {
        HistoryRole.User -> "user"
        HistoryRole.Assistant -> "assistant"
    }

private fun ApiSettings.openRouterTemperature(): Float? {
    if (!isApiControlEnabled) {
        return null
    }

    return temperature.coerceIn(0f, 1f) * 2f
}

private fun ApiSettings.openRouterMaxTokens(): Int? {
    if (!isApiControlEnabled || maxTokens <= 0) {
        return null
    }

    return maxTokens
}

private fun ApiSettings.openRouterStop(): List<String>? {
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
    val temperature: Float? = null,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    val stop: List<String>? = null,
)

@Serializable
private data class ApiChatMessage(
    val role: String,
    val content: String,
)

@Serializable
private data class ApiErrorResponse(
    val error: ApiError? = null,
)

@Serializable
private data class ApiError(
    val message: String? = null,
)

private const val ChatCompletionsUrl = "https://openrouter.ai/api/v1/chat/completions"
private const val ConnectTimeoutMillis = 30_000L
private const val DefaultRequestTimeoutMillis = 180_000L
private const val OpenRouterChatRequestTimeoutMillis = 300_000L
