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
import com.sibgear.deepseek.chat.data.ollama.internal.model.OllamaChatResponse
import com.sibgear.deepseek.chat.domain.interactor.ChatContextPlanner
import com.sibgear.deepseek.chat.domain.model.AgentResponse
import com.sibgear.deepseek.chat.domain.model.AiRequestData
import com.sibgear.deepseek.chat.domain.model.ChatMemoryRetrievalPlan
import com.sibgear.deepseek.chat.domain.repository.AiChatRepository
import com.sibgear.deepseek.chat.history.domain.interactor.ChatHistoryInteractor
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlin.time.TimeSource

class OllamaChatRepository(
    private val historyInteractor: ChatHistoryInteractor,
    private val memoryInteractor: AssistantMemoryInteractor? = null,
    private val baseUrl: String = DefaultOllamaBaseUrl,
    private val client: HttpClient = defaultOllamaHttpClient(),
    private val contextPlanner: ChatContextPlanner = ChatContextPlanner(),
) : AiChatRepository {
    private val json = ollamaJson()
    private val apiUrl = baseUrl.trimEnd('/')

    override suspend fun sendMessage(request: AiRequestData): AgentResponse {
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
            )
            val response = client.post("$apiUrl/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(apiRequest)
                timeout {
                    connectTimeoutMillis = ConnectTimeoutMillis
                    socketTimeoutMillis = DefaultRequestTimeoutMillis
                    requestTimeoutMillis = DefaultRequestTimeoutMillis
                }
            }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                throw IllegalStateException(formatApiError(response.status.value, response.status.description, body))
            }

            val completion = body.decodeOllamaChatResponse(json)
            val content = completion.message?.content.orEmpty().ifBlank {
                "Ошибка Ollama API: пустой ответ модели."
            }
            val messages = historyInteractor.add(
                request.toOllamaAssistantHistoryMessage(
                    content = content,
                    responseTimeMs = startedAt.elapsedNow().inWholeMilliseconds,
                    response = completion,
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
    var lastChunk: OllamaChatResponse? = null

    chunks.forEach { chunkBody ->
        val chunk = json.decodeFromString<OllamaChatResponse>(chunkBody)
        chunk.message?.let { message ->
            if (message.role.isNotBlank()) {
                role = message.role
            }
            content.append(message.content)
        }
        lastChunk = chunk
    }

    return OllamaChatResponse(
        message = OllamaChatMessage(
            role = role,
            content = content.toString(),
        ),
        done = lastChunk?.done ?: false,
        promptEvalCount = lastChunk?.promptEvalCount,
        evalCount = lastChunk?.evalCount,
    )
}

private data class PreparedMemory(
    val effectiveSystemPrompt: String,
)
