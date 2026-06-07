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
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
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
        install(ContentNegotiation) {
            json(json)
        }
    }

    override suspend fun sendMessage(request: AiRequestData): AgentResponse {
        history.add(ChatMessage(role = ChatRole.User, content = request.prompt))
        val startedAt = TimeSource.Monotonic.markNow()

        return try {
            val response = client.post(request.model.provider.chatCompletionsUrl) {
                bearerAuth(request.apiKey)
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(
                    ChatCompletionRequest(
                        model = request.model.id,
                        messages = request.apiMessages(history.getMessages()),
                        stream = false,
                        thinking = request.model.provider.thinking,
                        temperature = request.apiSettings.deepSeekTemperature(),
                        maxTokens = request.apiSettings.deepSeekMaxTokens(),
                        stop = request.apiSettings.deepSeekStop(),
                    ),
                )
            }

            val body = response.bodyAsText()
            val responseTimeMs = startedAt.elapsedNow().inWholeMilliseconds
            if (!response.status.isSuccess()) {
                val errorContent = formatApiError(
                    provider = request.model.provider,
                    statusCode = response.status.value,
                    statusDescription = response.status.description,
                    body = body,
                )

                return AgentResponse(
                    messages = history.add(
                        request.assistantMessage(
                            content = errorContent,
                            responseTimeMs = responseTimeMs,
                        ),
                    ),
                )
            }

            val completion = json.decodeFromString<ChatCompletionResponse>(body)
            val assistantContent = completion.choices.firstOrNull()?.message?.content?.takeIf { it.isNotBlank() }
                ?: "${request.model.provider.title} вернул пустой ответ."

            AgentResponse(
                messages = history.add(
                    request.assistantMessage(
                        content = assistantContent,
                        responseTimeMs = responseTimeMs,
                        usage = completion.usage,
                    ),
                ),
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Throwable) {
            val errorContent = "Ошибка запроса: ${exception.message ?: exception::class.simpleName ?: "unknown"}"
            AgentResponse(
                messages = history.add(
                    request.assistantMessage(
                        content = errorContent,
                        responseTimeMs = startedAt.elapsedNow().inWholeMilliseconds,
                    ),
                ),
            )
        }
    }

    override suspend fun loadOpenRouterModels(apiKey: String): List<AiModel> {
        val response = client.get("https://openrouter.ai/api/v1/models") {
            bearerAuth(apiKey)
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

private fun AiRequestData.assistantMessage(
    content: String,
    responseTimeMs: Long,
    usage: AiResponseUsage? = null,
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
