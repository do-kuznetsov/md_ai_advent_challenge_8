package com.sibgear.deepseek.chat.data.openrouter.external.repository

import com.sibgear.deepseek.chat.data.openrouter.internal.mapper.toChatMessages
import com.sibgear.deepseek.chat.data.openrouter.internal.mapper.toContextMessages
import com.sibgear.deepseek.chat.data.openrouter.internal.mapper.mergeStickyFacts
import com.sibgear.deepseek.chat.data.openrouter.internal.mapper.toOpenRouterAssistantHistoryMessage
import com.sibgear.deepseek.chat.data.openrouter.internal.mapper.toOpenRouterChatCompletionRequest
import com.sibgear.deepseek.chat.data.openrouter.internal.mapper.toOpenRouterCompressionSummaryHistoryMessage
import com.sibgear.deepseek.chat.data.openrouter.internal.mapper.toOpenRouterUserHistoryMessage
import com.sibgear.deepseek.chat.data.openrouter.internal.mapper.toHistoryFacts
import com.sibgear.deepseek.chat.data.openrouter.internal.mapper.toStickyFacts
import com.sibgear.deepseek.chat.data.openrouter.internal.model.OpenRouterApiErrorResponse
import com.sibgear.deepseek.chat.data.openrouter.internal.model.OpenRouterCompletionResult
import com.sibgear.deepseek.chat.data.openrouter.internal.repository.OpenRouterMaxRetries
import com.sibgear.deepseek.chat.data.openrouter.internal.repository.OpenRouterRetryDelayMillis
import com.sibgear.deepseek.chat.data.openrouter.internal.repository.OpenRouterStreamAccumulator
import com.sibgear.deepseek.chat.data.openrouter.internal.repository.OpenRouterStreamResult
import com.sibgear.deepseek.chat.data.openrouter.internal.repository.isOpenRouterRetryableStatus
import com.sibgear.deepseek.chat.data.openrouter.internal.repository.isOpenRouterRetryableTransportError
import com.sibgear.deepseek.chat.data.openrouter.internal.repository.isTimeoutError
import com.sibgear.deepseek.chat.domain.interactor.ChatContextPlanner
import com.sibgear.deepseek.chat.domain.model.AgentResponse
import com.sibgear.deepseek.chat.domain.model.AiRequestData
import com.sibgear.deepseek.chat.domain.model.ContextManagementMode
import com.sibgear.deepseek.chat.domain.model.ContextMessage
import com.sibgear.deepseek.chat.domain.model.StickyFact
import com.sibgear.deepseek.chat.domain.repository.AiChatRepository
import com.sibgear.deepseek.chat.history.domain.interactor.ChatHistoryInteractor
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessage
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
            val historyMessages = historyInteractor.getMessages()
            val stickyFacts = historyInteractor.getFacts().toStickyFacts()
            val completion = sendCompletionWithRetry(
                request = request,
                contextMessages = contextPlanner.plan(
                    messages = historyMessages.toContextMessages(),
                    contextManagementSettings = request.contextManagementSettings,
                    stickyFacts = stickyFacts,
                ).apiMessages,
                includeSystemPrompt = true,
                servicePrompt = null,
            ) { retryCount = it }
            val messagesWithAssistant = historyInteractor.add(
                request.toOpenRouterAssistantHistoryMessage(
                    content = completion.content,
                    responseTimeMs = startedAt.elapsedNow().inWholeMilliseconds,
                    usage = completion.usage,
                    retryCount = retryCount,
                ),
            )
            val updatedStickyFacts = if (!completion.isError &&
                request.contextManagementSettings.mode == ContextManagementMode.StickyFacts
            ) {
                updateStickyFacts(request, messagesWithAssistant, stickyFacts)
            } else {
                stickyFacts
            }
            AgentResponse(
                messages = if (completion.isError) {
                    messagesWithAssistant
                } else {
                    maybeCompressContext(request, messagesWithAssistant)
                }.toChatMessages(),
                stickyFacts = updatedStickyFacts,
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
                stickyFacts = historyInteractor.getFacts().toStickyFacts(),
            )
        }
    }

    private suspend fun updateStickyFacts(
        request: AiRequestData,
        currentMessages: List<HistoryMessage>,
        currentFacts: List<StickyFact>,
    ): List<StickyFact> {
        val updateRequest = contextPlanner.plan(
            messages = currentMessages.toContextMessages(),
            contextManagementSettings = request.contextManagementSettings,
            stickyFacts = currentFacts,
        ).stickyFactsUpdateRequest ?: return currentFacts

        val update = runCatching {
            sendCompletionWithRetry(
                request = request,
                contextMessages = updateRequest.messages,
                includeSystemPrompt = false,
                servicePrompt = updateRequest.prompt,
                onRetryCountChanged = {},
            )
        }.getOrElse { exception ->
            if (exception is CancellationException) {
                throw exception
            }
            return currentFacts
        }
        if (update.isError) {
            return currentFacts
        }

        val updatedFacts = update.content.mergeStickyFacts(currentFacts, json) ?: return currentFacts
        historyInteractor.replaceFacts(updatedFacts.toHistoryFacts())
        return updatedFacts
    }

    private suspend fun sendCompletionWithRetry(
        request: AiRequestData,
        contextMessages: List<ContextMessage>,
        includeSystemPrompt: Boolean,
        servicePrompt: String?,
        onRetryCountChanged: (Int) -> Unit,
    ): OpenRouterCompletionResult {
        var retryCount = 0

        while (true) {
            try {
                val completion = sendStreamingCompletion(
                    request = request,
                    contextMessages = contextMessages,
                    includeSystemPrompt = includeSystemPrompt,
                    servicePrompt = servicePrompt,
                )
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

    private suspend fun maybeCompressContext(
        request: AiRequestData,
        currentMessages: List<HistoryMessage>,
    ): List<HistoryMessage> {
        val compressionRequest = contextPlanner.plan(
            messages = currentMessages.toContextMessages(),
            contextManagementSettings = request.contextManagementSettings,
        ).compressionRequest ?: return currentMessages

        val startedAt = TimeSource.Monotonic.markNow()
        var retryCount = 0
        return try {
            val compression = sendCompletionWithRetry(
                request = request,
                contextMessages = compressionRequest.messages,
                includeSystemPrompt = false,
                servicePrompt = compressionRequest.prompt,
            ) { retryCount = it }
            if (compression.isError) {
                historyInteractor.add(
                    request.toOpenRouterAssistantHistoryMessage(
                        content = "Ошибка сжатия истории: ${compression.content}",
                        responseTimeMs = startedAt.elapsedNow().inWholeMilliseconds,
                        retryCount = retryCount,
                    ),
                )
            } else {
                historyInteractor.add(
                    request.toOpenRouterCompressionSummaryHistoryMessage(
                        content = compression.content,
                        responseTimeMs = startedAt.elapsedNow().inWholeMilliseconds,
                        usage = compression.usage,
                        retryCount = retryCount,
                    ),
                )
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Throwable) {
            historyInteractor.add(
                request.toOpenRouterAssistantHistoryMessage(
                    content = "Ошибка сжатия истории: ${formatException(exception)}",
                    responseTimeMs = startedAt.elapsedNow().inWholeMilliseconds,
                    retryCount = retryCount,
                ),
            )
        }
    }

    private suspend fun sendStreamingCompletion(
        request: AiRequestData,
        contextMessages: List<ContextMessage>,
        includeSystemPrompt: Boolean,
        servicePrompt: String?,
    ): OpenRouterCompletionResult {
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
                    contextMessages = contextMessages,
                    includeSystemPrompt = includeSystemPrompt,
                    servicePrompt = servicePrompt,
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
                isError = true,
            )
        }

        val stream = response.readOpenRouterStream()
        val content = stream.content.takeIf { it.isNotBlank() }
            ?: "OpenRouter вернул пустой ответ."

        return OpenRouterCompletionResult(
            content = content,
            usage = stream.usage,
            isError = stream.hasStreamError,
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
