package com.sibgear.deepseek.chat.data.deepseek.external.repository

import com.sibgear.deepseek.chat.data.deepseek.internal.mapper.toChatMessages
import com.sibgear.deepseek.chat.data.deepseek.internal.mapper.toContextMessages
import com.sibgear.deepseek.chat.data.deepseek.internal.mapper.toDeepSeekAssistantHistoryMessage
import com.sibgear.deepseek.chat.data.deepseek.internal.mapper.toDeepSeekChatCompletionRequest
import com.sibgear.deepseek.chat.data.deepseek.internal.mapper.toDeepSeekCompressionSummaryHistoryMessage
import com.sibgear.deepseek.chat.data.deepseek.internal.mapper.toDeepSeekUserHistoryMessage
import com.sibgear.deepseek.chat.data.deepseek.internal.model.DeepSeekApiErrorResponse
import com.sibgear.deepseek.chat.data.deepseek.internal.model.DeepSeekChatCompletionResponse
import com.sibgear.deepseek.chat.data.deepseek.internal.model.DeepSeekResponseUsage
import com.sibgear.deepseek.chat.domain.interactor.ChatContextPlanner
import com.sibgear.deepseek.chat.domain.model.AgentResponse
import com.sibgear.deepseek.chat.domain.model.AiRequestData
import com.sibgear.deepseek.chat.domain.model.ContextMessage
import com.sibgear.deepseek.chat.domain.repository.AiChatRepository
import com.sibgear.deepseek.chat.history.domain.interactor.ChatHistoryInteractor
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessage
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
    private val contextPlanner: ChatContextPlanner = ChatContextPlanner(),
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
        historyInteractor.add(request.toDeepSeekUserHistoryMessage())
        val startedAt = TimeSource.Monotonic.markNow()

        return try {
            val completion = sendCompletion(
                request = request,
                contextMessages = contextPlanner.plan(
                    messages = historyInteractor.getMessages().toContextMessages(),
                    compressionSettings = request.compressionSettings,
                ).apiMessages,
                includeSystemPrompt = true,
            )
            val messagesWithAssistant = historyInteractor.add(
                request.toDeepSeekAssistantHistoryMessage(
                    content = completion.content,
                    responseTimeMs = startedAt.elapsedNow().inWholeMilliseconds,
                    usage = completion.usage,
                )
            )

            AgentResponse(
                messages = if (completion.isError) {
                    messagesWithAssistant
                } else {
                    maybeCompressContext(request, messagesWithAssistant)
                }.toChatMessages(),
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

    private suspend fun maybeCompressContext(
        request: AiRequestData,
        currentMessages: List<HistoryMessage>,
    ): List<HistoryMessage> {
        val compressionRequest = contextPlanner.plan(
            messages = currentMessages.toContextMessages(),
            compressionSettings = request.compressionSettings,
        ).compressionRequest ?: return currentMessages

        val startedAt = TimeSource.Monotonic.markNow()
        return try {
            val compression = sendCompletion(
                request = request,
                contextMessages = compressionRequest.messages,
                includeSystemPrompt = false,
                servicePrompt = compressionRequest.prompt,
            )
            if (compression.isError) {
                historyInteractor.add(
                    request.toDeepSeekAssistantHistoryMessage(
                        content = "Ошибка сжатия истории: ${compression.content}",
                        responseTimeMs = startedAt.elapsedNow().inWholeMilliseconds,
                    ),
                )
            } else {
                historyInteractor.add(
                    request.toDeepSeekCompressionSummaryHistoryMessage(
                        content = compression.content,
                        responseTimeMs = startedAt.elapsedNow().inWholeMilliseconds,
                        usage = compression.usage,
                    ),
                )
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Throwable) {
            historyInteractor.add(
                request.toDeepSeekAssistantHistoryMessage(
                    content = "Ошибка сжатия истории: ${exception.message ?: exception::class.simpleName ?: "unknown"}",
                    responseTimeMs = startedAt.elapsedNow().inWholeMilliseconds,
                ),
            )
        }
    }

    private suspend fun sendCompletion(
        request: AiRequestData,
        contextMessages: List<ContextMessage>,
        includeSystemPrompt: Boolean,
        servicePrompt: String? = null,
    ): DeepSeekCompletionResult {
        val response = client.post(ChatCompletionsUrl) {
            bearerAuth(apiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                request.toDeepSeekChatCompletionRequest(
                    contextMessages = contextMessages,
                    includeSystemPrompt = includeSystemPrompt,
                    servicePrompt = servicePrompt,
                ),
            )
        }

        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            return DeepSeekCompletionResult(
                content = formatApiError(
                    statusCode = response.status.value,
                    statusDescription = response.status.description,
                    body = body,
                ),
                isError = true,
            )
        }

        val completion = json.decodeFromString<DeepSeekChatCompletionResponse>(body)
        val content = completion.choices.firstOrNull()?.message?.content?.takeIf { it.isNotBlank() }
            ?: "DeepSeek вернул пустой ответ."

        return DeepSeekCompletionResult(
            content = content,
            usage = completion.usage,
        )
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

private data class DeepSeekCompletionResult(
    val content: String,
    val usage: DeepSeekResponseUsage? = null,
    val isError: Boolean = false,
)

private const val ChatCompletionsUrl = "https://api.deepseek.com/chat/completions"
private const val ConnectTimeoutMillis = 30_000L
private const val RequestTimeoutMillis = 180_000L
