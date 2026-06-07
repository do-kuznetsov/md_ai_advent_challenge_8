package com.sibgear.deepseek.data

import com.sibgear.deepseek.domain.AgentResponse
import com.sibgear.deepseek.domain.AiProvider
import com.sibgear.deepseek.domain.ApiSettings
import com.sibgear.deepseek.domain.ChatMessage
import com.sibgear.deepseek.domain.ChatMessageFooter
import com.sibgear.deepseek.domain.ChatRole
import com.sibgear.deepseek.domain.AiModel
import com.sibgear.deepseek.domain.AiRepository
import com.sibgear.deepseek.domain.AiRequestData
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.TimeSource

class KtorAiRepository(
    private val history: InMemoryChatHistory = InMemoryChatHistory(),
) : AiRepository {
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
        history.add(ChatMessage(role = ChatRole.User, content = request.prompt))
        val startedAt = TimeSource.Monotonic.markNow()
        var retryCount = 0

        return try {
            val completion = when (request.model.provider) {
                AiProvider.DeepSeek -> sendNonStreamingCompletion(request)
                AiProvider.OpenRouter -> sendOpenRouterCompletionWithRetry(request) { retryCount = it }
            }

            AgentResponse(
                messages = history.add(
                    request.assistantMessage(
                        content = completion.content,
                        responseTimeMs = startedAt.elapsedNow().inWholeMilliseconds,
                        usage = completion.usage,
                        retryCount = retryCount,
                    ),
                ),
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Throwable) {
            AgentResponse(
                messages = history.add(
                    request.assistantMessage(
                        content = request.model.provider.formatException(exception),
                        responseTimeMs = startedAt.elapsedNow().inWholeMilliseconds,
                        retryCount = retryCount,
                    ),
                ),
            )
        }
    }

    private suspend fun sendNonStreamingCompletion(request: AiRequestData): CompletionResult {
        val response = client.post(request.model.provider.chatCompletionsUrl) {
            bearerAuth(request.apiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                request.chatCompletionRequest(
                    stream = false,
                    historyMessages = history.getMessages(),
                ),
            )
        }

        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            return CompletionResult(
                content = formatApiError(
                    provider = request.model.provider,
                    statusCode = response.status.value,
                    statusDescription = response.status.description,
                    body = body,
                ),
            )
        }

        val completion = json.decodeFromString<ChatCompletionResponse>(body)
        val assistantContent = completion.choices.firstOrNull()?.message?.content?.takeIf { it.isNotBlank() }
            ?: "${request.model.provider.title} вернул пустой ответ."

        return CompletionResult(
            content = assistantContent,
            usage = completion.usage,
        )
    }

    private suspend fun sendOpenRouterCompletionWithRetry(
        request: AiRequestData,
        onRetryCountChanged: (Int) -> Unit,
    ): CompletionResult {
        var retryCount = 0

        while (true) {
            try {
                val completion = sendOpenRouterStreamingCompletion(request)
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

    private suspend fun sendOpenRouterStreamingCompletion(request: AiRequestData): CompletionResult {
        val response = client.post(AiProvider.OpenRouter.chatCompletionsUrl) {
            bearerAuth(request.apiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            header(HttpHeaders.Accept, "text/event-stream")
            timeout {
                connectTimeoutMillis = ConnectTimeoutMillis
                socketTimeoutMillis = OpenRouterChatRequestTimeoutMillis
                requestTimeoutMillis = OpenRouterChatRequestTimeoutMillis
            }
            setBody(
                request.chatCompletionRequest(
                    stream = true,
                    historyMessages = history.getMessages(),
                ),
            )
        }

        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            return CompletionResult(
                content = formatApiError(
                    provider = AiProvider.OpenRouter,
                    statusCode = response.status.value,
                    statusDescription = response.status.description,
                    body = body,
                ),
                isRetryable = response.status.isOpenRouterRetryableStatus(),
            )
        }

        val stream = response.readOpenRouterStream()
        val content = stream.content.takeIf { it.isNotBlank() }
            ?: "${AiProvider.OpenRouter.title} вернул пустой ответ."

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

    override suspend fun loadOpenRouterModels(apiKey: String): List<AiModel> {
        val response = client.get("https://openrouter.ai/api/v1/models") {
            bearerAuth(apiKey)
            timeout {
                connectTimeoutMillis = ConnectTimeoutMillis
                socketTimeoutMillis = ModelsRequestTimeoutMillis
                requestTimeoutMillis = ModelsRequestTimeoutMillis
            }
        }

        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw IllegalStateException(
                formatApiError(
                    provider = AiProvider.OpenRouter,
                    statusCode = response.status.value,
                    statusDescription = response.status.description,
                    body = body,
                ),
            )
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
        provider: AiProvider,
        statusCode: Int,
        statusDescription: String,
        body: String,
    ): String {
        val apiMessage = runCatching {
            json.decodeFromString<ApiErrorResponse>(body).error?.message
        }.getOrNull()

        val message = apiMessage ?: body.take(600).ifBlank { "без тела ответа" }
        return "Ошибка ${provider.title} API: HTTP $statusCode $statusDescription\n$message"
    }
}

private data class CompletionResult(
    val content: String,
    val usage: AiResponseUsage? = null,
    val isRetryable: Boolean = false,
)

private const val ConnectTimeoutMillis = 30_000L
private const val DefaultRequestTimeoutMillis = 180_000L
private const val OpenRouterChatRequestTimeoutMillis = 300_000L
private const val ModelsRequestTimeoutMillis = 60_000L

private val AiRequestData.apiKey: String
    get() = when (model.provider) {
        AiProvider.DeepSeek -> deepSeekApiKey
        AiProvider.OpenRouter -> openRouterApiKey
    }

private val AiProvider.chatCompletionsUrl: String
    get() = when (this) {
        AiProvider.DeepSeek -> "https://api.deepseek.com/chat/completions"
        AiProvider.OpenRouter -> "https://openrouter.ai/api/v1/chat/completions"
    }

private val AiProvider.thinking: Thinking?
    get() = when (this) {
        AiProvider.DeepSeek -> Thinking(type = "disabled")
        AiProvider.OpenRouter -> null
    }

private val AiProvider.title: String
    get() = when (this) {
        AiProvider.DeepSeek -> "DeepSeek"
        AiProvider.OpenRouter -> "OpenRouter"
    }

private fun AiProvider.formatException(exception: Throwable): String {
    if (this == AiProvider.OpenRouter && exception.isTimeoutError()) {
        return "OpenRouter timeout after ${OpenRouterChatRequestTimeoutMillis / 1_000}s"
    }

    return "Ошибка запроса: ${exception.message ?: exception::class.simpleName ?: "unknown"}"
}

private fun AiRequestData.chatCompletionRequest(
    stream: Boolean,
    historyMessages: List<ChatMessage>,
): ChatCompletionRequest =
    ChatCompletionRequest(
        model = model.id,
        messages = apiMessages(historyMessages),
        stream = stream,
        thinking = model.provider.thinking,
        temperature = apiSettings.deepSeekTemperature(),
        maxTokens = apiSettings.deepSeekMaxTokens(),
        stop = apiSettings.deepSeekStop(),
    )

private fun AiRequestData.assistantMessage(
    content: String,
    responseTimeMs: Long,
    usage: AiResponseUsage? = null,
    retryCount: Int = 0,
): ChatMessage =
    ChatMessage(
        role = ChatRole.Assistant,
        content = content,
        sourceLabel = "${model.provider.title} / ${model.displayName}",
        footer = ChatMessageFooter(
            responseTimeMs = responseTimeMs,
            promptTokens = usage?.promptTokens,
            completionTokens = usage?.completionTokens,
            totalTokens = usage?.displayTotalTokens,
            cost = usage?.costFor(model),
            retryCount = retryCount,
        ),
    )

private fun AiRequestData.apiMessages(historyMessages: List<ChatMessage>): List<ApiChatMessage> {
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
    val thinking: Thinking? = null,
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
    val usage: AiResponseUsage? = null,
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
private data class ApiErrorResponse(
    val error: ApiError? = null,
)

@Serializable
private data class ApiError(
    val message: String? = null,
)

@Serializable
private data class OpenRouterModelsResponse(
    val data: List<OpenRouterModelDto> = emptyList(),
)

@Serializable
private data class OpenRouterModelDto(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    @kotlinx.serialization.SerialName("context_length")
    val contextLength: Int? = null,
    @kotlinx.serialization.SerialName("supported_parameters")
    val supportedParameters: List<String>? = null,
)
