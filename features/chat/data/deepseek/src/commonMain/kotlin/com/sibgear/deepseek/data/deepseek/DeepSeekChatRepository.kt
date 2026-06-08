package com.sibgear.deepseek.data.deepseek

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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
                    request.chatCompletionRequest(
                        historyMessages = historyInteractor.getMessages(),
                    ),
                )
            }

            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                return AgentResponse(
                    messages = historyInteractor.add(
                        request.assistantHistoryMessage(
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

            val completion = json.decodeFromString<ChatCompletionResponse>(body)
            val assistantContent = completion.choices.firstOrNull()?.message?.content?.takeIf { it.isNotBlank() }
                ?: "DeepSeek вернул пустой ответ."

            AgentResponse(
                messages = historyInteractor.add(
                    request.assistantHistoryMessage(
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
                    request.assistantHistoryMessage(
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
            json.decodeFromString<ApiErrorResponse>(body).error?.message
        }.getOrNull()

        val message = apiMessage ?: body.take(600).ifBlank { "без тела ответа" }
        return "Ошибка DeepSeek API: HTTP $statusCode $statusDescription\n$message"
    }
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
        stream = false,
        thinking = Thinking(type = "disabled"),
        temperature = apiSettings.deepSeekTemperature(),
        maxTokens = apiSettings.deepSeekMaxTokens(),
        stop = apiSettings.deepSeekStop(),
    )
}

private fun AiRequestData.assistantHistoryMessage(
    content: String,
    responseTimeMs: Long,
    usage: DeepSeekResponseUsage? = null,
): HistoryMessage =
    HistoryMessage(
        role = HistoryRole.Assistant,
        content = content,
        sourceLabel = "DeepSeek / ${model.displayName}",
        footer = HistoryMessageFooter(
            responseTimeMs = responseTimeMs,
            promptTokens = usage?.promptTokens,
            completionTokens = usage?.completionTokens,
            totalTokens = usage?.displayTotalTokens,
            cost = usage?.deepSeekCost(model.id),
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
private data class Thinking(
    val type: String,
)

@Serializable
private data class ChatCompletionResponse(
    val choices: List<Choice> = emptyList(),
    val usage: DeepSeekResponseUsage? = null,
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

private const val ChatCompletionsUrl = "https://api.deepseek.com/chat/completions"
private const val ConnectTimeoutMillis = 30_000L
private const val RequestTimeoutMillis = 180_000L
