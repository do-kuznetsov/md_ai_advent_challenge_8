package com.sibgear.deepseek.chat.data.ollama.external.repository

import com.sibgear.deepseek.assistant.memory.domain.interactor.AssistantMemoryInteractor
import com.sibgear.deepseek.chat.data.ollama.internal.mapper.toChatBranches
import com.sibgear.deepseek.chat.data.ollama.internal.mapper.toChatInvariants
import com.sibgear.deepseek.chat.data.ollama.internal.mapper.toChatMemoryItems
import com.sibgear.deepseek.chat.data.ollama.internal.mapper.toChatMessages
import com.sibgear.deepseek.chat.data.ollama.internal.mapper.toContextMessages
import com.sibgear.deepseek.chat.data.ollama.internal.mapper.toOllamaAssistantHistoryMessage
import com.sibgear.deepseek.chat.data.ollama.internal.mapper.toOllamaChatRequest
import com.sibgear.deepseek.chat.data.ollama.internal.mapper.toOllamaUserHistoryMessage
import com.sibgear.deepseek.chat.data.ollama.internal.mapper.toStickyFacts
import com.sibgear.deepseek.chat.data.ollama.internal.model.OllamaApiErrorResponse
import com.sibgear.deepseek.chat.data.ollama.internal.model.OllamaChatMessage
import com.sibgear.deepseek.chat.data.ollama.internal.model.OllamaChatRequest
import com.sibgear.deepseek.chat.data.ollama.internal.model.OllamaChatResponse
import com.sibgear.deepseek.chat.domain.interactor.ChatContextPlanner
import com.sibgear.deepseek.chat.domain.model.AgentResponse
import com.sibgear.deepseek.chat.domain.model.AiRequestData
import com.sibgear.deepseek.chat.domain.model.ChatMemoryRetrievalPlan
import com.sibgear.deepseek.chat.domain.model.StreamingChatDelta
import com.sibgear.deepseek.chat.domain.model.StreamingChatDeltaType
import com.sibgear.deepseek.chat.domain.repository.AiChatRepository
import com.sibgear.deepseek.chat.domain.repository.StreamingAiChatRepository
import com.sibgear.deepseek.chat.history.domain.interactor.ChatHistoryInteractor
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readLine
import kotlinx.coroutines.CancellationException
import kotlin.time.TimeSource

class OllamaChatRepository(
    private val historyInteractor: ChatHistoryInteractor,
    private val memoryInteractor: AssistantMemoryInteractor? = null,
    private val baseUrl: String = DefaultOllamaBaseUrl,
    private val client: HttpClient = defaultOllamaHttpClient(),
    private val contextPlanner: ChatContextPlanner = ChatContextPlanner(),
) : StreamingAiChatRepository {
    private val json = ollamaJson()
    private val apiUrl = baseUrl.trimEnd('/')

    override suspend fun sendMessage(request: AiRequestData): AgentResponse {
        return sendMessageInternal(request, stream = false, onDelta = null)
    }

    override suspend fun sendStreamingMessage(
        request: AiRequestData,
        onDelta: suspend (StreamingChatDelta) -> Unit,
    ): AgentResponse {
        return sendMessageInternal(request, stream = true, onDelta = onDelta)
    }

    private suspend fun sendMessageInternal(
        request: AiRequestData,
        stream: Boolean,
        onDelta: (suspend (StreamingChatDelta) -> Unit)?,
    ): AgentResponse {
        val startedAt = TimeSource.Monotonic.markNow()
        val memory = prepareMemory(request)

        if (request.persistUserMessage) {
            historyInteractor.add(request.toOllamaUserHistoryMessage())
        }

        return try {
            val historyMessages = historyInteractor.getMessages()
            val plannedContext = contextPlanner.plan(
                messages = historyMessages.toContextMessages(),
                contextManagementSettings = request.contextManagementSettings,
                stickyFacts = historyInteractor.getFacts().toStickyFacts(),
                branches = historyInteractor.getBranches().toChatBranches(),
                activeBranchId = historyMessages.lastOrNull { it.branchId != null }?.branchId,
            ).apiMessages
            val apiRequest = request.toOllamaChatRequest(
                contextMessages = plannedContext,
                effectiveSystemPrompt = memory.effectiveSystemPrompt,
                servicePrompt = request.prompt.takeUnless { request.persistUserMessage },
                stream = stream,
            )
            val completion = executeChatRequest(apiRequest, stream, onDelta)
            val content = completion.message?.content.orEmpty().ifBlank {
                "Ошибка Ollama API: пустой ответ модели."
            }
            val thinkingContent = completion.message?.thinking.orEmpty().takeIf { it.isNotBlank() }
            val messages = historyInteractor.add(
                request.toOllamaAssistantHistoryMessage(
                    content = content,
                    responseTimeMs = startedAt.elapsedNow().inWholeMilliseconds,
                    response = completion,
                    thinkingContent = thinkingContent,
                ),
            )
            AgentResponse(
                messages = messages.toChatMessages(),
                stickyFacts = historyInteractor.getFacts().toStickyFacts(),
                branches = historyInteractor.getBranches().toChatBranches(),
                activeBranchId = messages.lastOrNull { it.branchId != null }?.branchId,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Throwable) {
            val messages = historyInteractor.add(
                request.toOllamaAssistantHistoryMessage(
                    content = "Ошибка Ollama: ${exception.message ?: exception::class.simpleName ?: "unknown"}",
                    responseTimeMs = startedAt.elapsedNow().inWholeMilliseconds,
                ),
            )
            AgentResponse(
                messages = messages.toChatMessages(),
                stickyFacts = historyInteractor.getFacts().toStickyFacts(),
                branches = historyInteractor.getBranches().toChatBranches(),
                activeBranchId = messages.lastOrNull { it.branchId != null }?.branchId,
            )
        }
    }

    private suspend fun executeChatRequest(
        apiRequest: OllamaChatRequest,
        stream: Boolean,
        onDelta: (suspend (StreamingChatDelta) -> Unit)?,
    ): OllamaChatResponse {
        if (stream && onDelta != null) {
            return client.preparePost("$apiUrl/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(apiRequest)
                timeout {
                    connectTimeoutMillis = ConnectTimeoutMillis
                    socketTimeoutMillis = DefaultRequestTimeoutMillis
                    requestTimeoutMillis = DefaultRequestTimeoutMillis
                }
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    val body = response.bodyAsText()
                    throw IllegalStateException(formatApiError(response.status.value, response.status.description, body))
                }
                response.readOllamaStream(onDelta)
            }
        }

        val response = client.post("$apiUrl/api/chat") {
            contentType(ContentType.Application.Json)
            setBody(apiRequest)
            timeout {
                connectTimeoutMillis = ConnectTimeoutMillis
                socketTimeoutMillis = DefaultRequestTimeoutMillis
                requestTimeoutMillis = DefaultRequestTimeoutMillis
            }
        }
        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            throw IllegalStateException(formatApiError(response.status.value, response.status.description, body))
        }
        return response.bodyAsText().decodeOllamaChatResponse(json)
    }

    private suspend fun HttpResponse.readOllamaStream(
        onDelta: suspend (StreamingChatDelta) -> Unit,
    ): OllamaChatResponse {
        val accumulator = OllamaStreamAccumulator(json)
        val channel = bodyAsChannel()

        try {
            while (true) {
                val line = channel.readLine() ?: break
                accumulator.acceptLine(line, onDelta)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Throwable) {
            accumulator.appendError(exception.message ?: exception::class.simpleName ?: "unknown")
        }

        return accumulator.result()
    }

    private suspend fun prepareMemory(request: AiRequestData): PreparedMemory {
        val memory = memoryInteractor ?: return PreparedMemory(request.systemPrompt)
        val items = runCatching { memory.getItems() }.getOrDefault(emptyList())
        val profile = runCatching { memory.getProfile().text }.getOrDefault("")
        val invariants = runCatching { memory.getInvariants() }.getOrDefault(emptyList())
        val injection = contextPlanner.memoryInjection(
            originalSystemPrompt = request.systemPrompt,
            invariants = invariants.toChatInvariants(),
            userProfile = profile,
            retrievalPlan = ChatMemoryRetrievalPlan(
                needShortTerm = true,
                needWorkingMemory = true,
                needLongTermMemory = true,
            ),
            availableMemory = items.toChatMemoryItems(),
        )
        return PreparedMemory(injection.effectiveSystemPrompt)
    }

    private fun formatApiError(
        statusCode: Int,
        statusDescription: String,
        body: String,
    ): String {
        val apiMessage = runCatching {
            json.decodeFromString<OllamaApiErrorResponse>(body).error
        }.getOrNull()
        val message = apiMessage ?: body.take(600).ifBlank { "без тела ответа" }
        return "Ошибка Ollama API: HTTP $statusCode $statusDescription\n$message"
    }
}

internal fun String.decodeOllamaChatResponse(json: kotlinx.serialization.json.Json): OllamaChatResponse {
    val chunks = lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toList()

    if (chunks.size <= 1) {
        return json.decodeFromString<OllamaChatResponse>(trim())
    }

    var role = "assistant"
    val content = StringBuilder()
    val thinking = StringBuilder()
    var lastChunk: OllamaChatResponse? = null

    chunks.forEach { chunkBody ->
        val chunk = json.decodeFromString<OllamaChatResponse>(chunkBody)
        chunk.message?.let { message ->
            if (message.role.isNotBlank()) {
                role = message.role
            }
            message.thinking?.let(thinking::append)
            content.append(message.content)
        }
        lastChunk = chunk
    }

    return OllamaChatResponse(
        message = OllamaChatMessage(
            role = role,
            content = content.toString(),
            thinking = thinking.toString().takeIf { it.isNotBlank() },
        ),
        done = lastChunk?.done ?: false,
        promptEvalCount = lastChunk?.promptEvalCount,
        evalCount = lastChunk?.evalCount,
    )
}

private class OllamaStreamAccumulator(
    private val json: kotlinx.serialization.json.Json,
) {
    private var role = "assistant"
    private val thinking = StringBuilder()
    private val content = StringBuilder()
    private var lastChunk: OllamaChatResponse? = null
    private var errorMessage: String? = null

    suspend fun acceptLine(
        line: String,
        onDelta: suspend (StreamingChatDelta) -> Unit,
    ) {
        val body = line.trim()
        if (body.isEmpty()) {
            return
        }

        val chunk = json.decodeFromString<OllamaChatResponse>(body)
        chunk.message?.let { message ->
            if (message.role.isNotBlank()) {
                role = message.role
            }
            message.thinking
                ?.takeIf { it.isNotEmpty() }
                ?.let { delta ->
                    thinking.append(delta)
                    onDelta(StreamingChatDelta(type = StreamingChatDeltaType.Thinking, text = delta))
                }
            message.content
                .takeIf { it.isNotEmpty() }
                ?.let { delta ->
                    content.append(delta)
                    onDelta(StreamingChatDelta(type = StreamingChatDeltaType.Content, text = delta))
                }
        }
        lastChunk = chunk
    }

    fun appendError(message: String) {
        errorMessage = message
    }

    fun result(): OllamaChatResponse {
        val finalContent = when {
            errorMessage == null -> content.toString()
            content.isBlank() -> "Ошибка Ollama stream: $errorMessage"
            else -> "${content}\n\nОшибка Ollama stream: $errorMessage"
        }

        return OllamaChatResponse(
            message = OllamaChatMessage(
                role = role,
                content = finalContent,
                thinking = thinking.toString().takeIf { it.isNotBlank() },
            ),
            done = lastChunk?.done ?: false,
            promptEvalCount = lastChunk?.promptEvalCount,
            evalCount = lastChunk?.evalCount,
        )
    }
}

private data class PreparedMemory(
    val effectiveSystemPrompt: String,
)
