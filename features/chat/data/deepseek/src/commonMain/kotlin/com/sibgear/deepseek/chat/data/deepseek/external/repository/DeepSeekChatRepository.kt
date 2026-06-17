package com.sibgear.deepseek.chat.data.deepseek.external.repository

import com.sibgear.deepseek.assistant.memory.domain.interactor.AssistantMemoryInteractor
import com.sibgear.deepseek.chat.data.deepseek.internal.mapper.toChatMessages
import com.sibgear.deepseek.chat.data.deepseek.internal.mapper.toChatMemoryItems
import com.sibgear.deepseek.chat.data.deepseek.internal.mapper.toChatMemoryRetrievalPlan
import com.sibgear.deepseek.chat.data.deepseek.internal.mapper.toContextMessages
import com.sibgear.deepseek.chat.data.deepseek.internal.mapper.toDeepSeekAssistantHistoryMessage
import com.sibgear.deepseek.chat.data.deepseek.internal.mapper.toDeepSeekChatCompletionRequest
import com.sibgear.deepseek.chat.data.deepseek.internal.mapper.toDeepSeekCompressionSummaryHistoryMessage
import com.sibgear.deepseek.chat.data.deepseek.internal.mapper.toDeepSeekUserHistoryMessage
import com.sibgear.deepseek.chat.data.deepseek.internal.mapper.toHistoryMemoryChanges
import com.sibgear.deepseek.chat.data.deepseek.internal.mapper.toHistoryMemoryItems
import com.sibgear.deepseek.chat.data.deepseek.internal.mapper.toHistoryMemoryLayers
import com.sibgear.deepseek.chat.data.deepseek.internal.mapper.toBranchRoutingDecision
import com.sibgear.deepseek.chat.data.deepseek.internal.mapper.toChatBranches
import com.sibgear.deepseek.chat.data.deepseek.internal.mapper.toHistoryBranches
import com.sibgear.deepseek.chat.data.deepseek.internal.mapper.toHistoryFacts
import com.sibgear.deepseek.chat.data.deepseek.internal.mapper.toMemoryCandidates
import com.sibgear.deepseek.chat.data.deepseek.internal.mapper.toMemoryUpdates
import com.sibgear.deepseek.chat.data.deepseek.internal.mapper.toStickyFacts
import com.sibgear.deepseek.chat.data.deepseek.internal.mapper.mergeStickyFacts
import com.sibgear.deepseek.chat.data.deepseek.internal.model.DeepSeekApiErrorResponse
import com.sibgear.deepseek.chat.data.deepseek.internal.model.DeepSeekChatCompletionResponse
import com.sibgear.deepseek.chat.data.deepseek.internal.model.DeepSeekResponseUsage
import com.sibgear.deepseek.chat.domain.interactor.ChatContextPlanner
import com.sibgear.deepseek.chat.domain.model.AgentResponse
import com.sibgear.deepseek.chat.domain.model.AiRequestData
import com.sibgear.deepseek.chat.domain.model.BranchSelection
import com.sibgear.deepseek.chat.domain.model.ChatBranch
import com.sibgear.deepseek.chat.domain.model.ContextManagementMode
import com.sibgear.deepseek.chat.domain.model.ContextMessage
import com.sibgear.deepseek.chat.domain.model.StickyFact
import com.sibgear.deepseek.chat.domain.repository.AiChatRepository
import com.sibgear.deepseek.chat.history.domain.interactor.ChatHistoryInteractor
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageMemoryMetadata
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
    private val memoryInteractor: AssistantMemoryInteractor? = null,
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

    override suspend fun sendMessage(request: AiRequestData): AgentResponse =
        if (request.contextManagementSettings.mode == ContextManagementMode.Branching) {
            sendBranchingMessage(request)
        } else {
            sendLinearMessage(request)
        }

    private suspend fun sendLinearMessage(request: AiRequestData): AgentResponse {
        val preparedMemory = prepareMemory(request)
        historyInteractor.add(request.toDeepSeekUserHistoryMessage(memory = preparedMemory.metadata))
        val startedAt = TimeSource.Monotonic.markNow()

        return try {
            val historyMessages = historyInteractor.getMessages()
            val stickyFacts = historyInteractor.getFacts().toStickyFacts()
            val completion = sendCompletion(
                request = request,
                contextMessages = contextPlanner.plan(
                    messages = historyMessages.toContextMessages(),
                    contextManagementSettings = request.contextManagementSettings,
                    stickyFacts = stickyFacts,
                ).apiMessages,
                includeSystemPrompt = true,
                effectiveSystemPrompt = preparedMemory.effectiveSystemPrompt,
            )
            val messagesWithAssistant = historyInteractor.add(
                request.toDeepSeekAssistantHistoryMessage(
                    content = completion.content,
                    responseTimeMs = startedAt.elapsedNow().inWholeMilliseconds,
                    usage = completion.usage,
                )
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
                branches = historyInteractor.getBranches().toChatBranches(),
                activeBranchId = messagesWithAssistant.lastOrNull { it.branchId != null }?.branchId,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Throwable) {
            val messages = historyInteractor.add(
                request.toDeepSeekAssistantHistoryMessage(
                    content = "Ошибка запроса: ${exception.message ?: exception::class.simpleName ?: "unknown"}",
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

    private suspend fun sendBranchingMessage(request: AiRequestData): AgentResponse {
        val startedAt = TimeSource.Monotonic.markNow()
        val preparedMemory = prepareMemory(request)
        val historyBeforeUser = historyInteractor.getMessages()
        var branchSelection = resolveBranch(
            request = request,
            branches = historyInteractor.getBranches().toChatBranches(),
            fallbackActiveBranchId = historyBeforeUser.lastOrNull { it.branchId != null }?.branchId,
        )
        historyInteractor.replaceBranches(branchSelection.branches.toHistoryBranches())
        historyInteractor.add(
            request.toDeepSeekUserHistoryMessage(
                branchId = branchSelection.activeBranchId,
                memory = preparedMemory.metadata,
            ),
        )

        return try {
            val historyMessages = historyInteractor.getMessages()
            val completion = sendCompletion(
                request = request,
                contextMessages = contextPlanner.plan(
                    messages = historyMessages.toContextMessages(),
                    contextManagementSettings = request.contextManagementSettings,
                    branches = branchSelection.branches,
                    activeBranchId = branchSelection.activeBranchId,
                ).apiMessages,
                includeSystemPrompt = true,
                effectiveSystemPrompt = preparedMemory.effectiveSystemPrompt,
            )
            val messagesWithAssistant = historyInteractor.add(
                request.toDeepSeekAssistantHistoryMessage(
                    content = completion.content,
                    responseTimeMs = startedAt.elapsedNow().inWholeMilliseconds,
                    usage = completion.usage,
                    branchId = branchSelection.activeBranchId,
                ),
            )
            branchSelection = if (completion.isError) {
                branchSelection
            } else {
                branchSelection.copy(
                    branches = updateBranchSummary(
                        request = request,
                        currentMessages = messagesWithAssistant,
                        branches = branchSelection.branches,
                        activeBranchId = branchSelection.activeBranchId,
                    ),
                )
            }

            AgentResponse(
                messages = messagesWithAssistant.toChatMessages(),
                stickyFacts = historyInteractor.getFacts().toStickyFacts(),
                branches = branchSelection.branches,
                activeBranchId = branchSelection.activeBranchId,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Throwable) {
            val messages = historyInteractor.add(
                request.toDeepSeekAssistantHistoryMessage(
                    content = "Ошибка запроса: ${exception.message ?: exception::class.simpleName ?: "unknown"}",
                    responseTimeMs = startedAt.elapsedNow().inWholeMilliseconds,
                    branchId = branchSelection.activeBranchId,
                ),
            )
            AgentResponse(
                messages = messages.toChatMessages(),
                stickyFacts = historyInteractor.getFacts().toStickyFacts(),
                branches = historyInteractor.getBranches().toChatBranches(),
                activeBranchId = branchSelection.activeBranchId,
            )
        }
    }

    private suspend fun resolveBranch(
        request: AiRequestData,
        branches: List<ChatBranch>,
        fallbackActiveBranchId: Int?,
    ): BranchSelection {
        val routingRequest = contextPlanner.branchRoutingRequest(
            branches = branches,
            userPrompt = request.prompt,
        )
        val decision = runCatching {
            sendCompletion(
                request = request,
                contextMessages = emptyList(),
                includeSystemPrompt = false,
                servicePrompt = routingRequest.prompt,
            )
        }.getOrElse { exception ->
            if (exception is CancellationException) {
                throw exception
            }
            return fallbackBranch(branches, fallbackActiveBranchId)
        }

        if (decision.isError) {
            return fallbackBranch(branches, fallbackActiveBranchId)
        }

        return contextPlanner.selectBranch(
            branches = branches,
            decision = decision.content.toBranchRoutingDecision(json),
        )
    }

    private fun fallbackBranch(
        branches: List<ChatBranch>,
        fallbackActiveBranchId: Int?,
    ): BranchSelection {
        if (fallbackActiveBranchId != null && branches.any { it.id == fallbackActiveBranchId }) {
            return BranchSelection(
                branches = branches,
                activeBranchId = fallbackActiveBranchId,
            )
        }

        return contextPlanner.fallbackBranch(branches)
    }

    private suspend fun updateBranchSummary(
        request: AiRequestData,
        currentMessages: List<HistoryMessage>,
        branches: List<ChatBranch>,
        activeBranchId: Int,
    ): List<ChatBranch> {
        val updateRequest = contextPlanner.plan(
            messages = currentMessages.toContextMessages(),
            contextManagementSettings = request.contextManagementSettings,
            branches = branches,
            activeBranchId = activeBranchId,
        ).branchSummaryUpdateRequest ?: return branches

        val update = runCatching {
            sendCompletion(
                request = request,
                contextMessages = updateRequest.messages,
                includeSystemPrompt = false,
                servicePrompt = updateRequest.prompt,
            )
        }.getOrElse { exception ->
            if (exception is CancellationException) {
                throw exception
            }
            return branches
        }
        if (update.isError) {
            return branches
        }

        val summary = update.content.trim().takeIf { it.isNotEmpty() } ?: return branches
        val updatedBranches = branches.map { branch ->
            if (branch.id == activeBranchId) {
                branch.copy(summary = summary)
            } else {
                branch
            }
        }
        historyInteractor.replaceBranches(updatedBranches.toHistoryBranches())
        return updatedBranches
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
            sendCompletion(
                request = request,
                contextMessages = updateRequest.messages,
                includeSystemPrompt = false,
                servicePrompt = updateRequest.prompt,
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

    private suspend fun maybeCompressContext(
        request: AiRequestData,
        currentMessages: List<HistoryMessage>,
    ): List<HistoryMessage> {
        val compressionRequest = contextPlanner.plan(
            messages = currentMessages.toContextMessages(),
            contextManagementSettings = request.contextManagementSettings,
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
        effectiveSystemPrompt: String? = null,
    ): DeepSeekCompletionResult {
        val response = client.post(ChatCompletionsUrl) {
            bearerAuth(apiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                request.toDeepSeekChatCompletionRequest(
                    contextMessages = contextMessages,
                    includeSystemPrompt = includeSystemPrompt,
                    servicePrompt = servicePrompt,
                    effectiveSystemPrompt = effectiveSystemPrompt,
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

    private suspend fun prepareMemory(request: AiRequestData): PreparedMemory {
        val memory = memoryInteractor ?: return PreparedMemory(
            effectiveSystemPrompt = request.systemPrompt,
            metadata = null,
        )

        val originalItems = runCatching { memory.getItems() }.getOrElse { exception ->
            if (exception is CancellationException) {
                throw exception
            }
            return PreparedMemory(
                effectiveSystemPrompt = request.systemPrompt,
                metadata = HistoryMessageMemoryMetadata(error = formatMemoryError(exception)),
            )
        }

        var currentItems = originalItems
        var changes = emptyList<com.sibgear.deepseek.chat.history.domain.model.HistoryMemoryChange>()
        val errors = mutableListOf<String>()

        val candidates = runCatching {
            val classificationRequest = contextPlanner.memoryClassificationRequest(request.prompt)
            sendCompletion(
                request = request,
                contextMessages = emptyList(),
                includeSystemPrompt = false,
                servicePrompt = classificationRequest.prompt,
            ).takeUnless { it.isError }
                ?.content
                ?.toMemoryCandidates(json)
                .orEmpty()
        }.getOrElse { exception ->
            if (exception is CancellationException) {
                throw exception
            }
            errors += formatMemoryError(exception)
            emptyList()
        }

        if (candidates.isNotEmpty()) {
            val updates = runCatching {
                val mutationRequest = contextPlanner.memoryMutationRequest(
                    currentMemory = currentItems.toChatMemoryItems(),
                    candidates = candidates,
                )
                sendCompletion(
                    request = request,
                    contextMessages = emptyList(),
                    includeSystemPrompt = false,
                    servicePrompt = mutationRequest.prompt,
                ).takeUnless { it.isError }
                    ?.content
                    ?.toMemoryUpdates(json)
                    .orEmpty()
            }.getOrElse { exception ->
                if (exception is CancellationException) {
                    throw exception
                }
                errors += formatMemoryError(exception)
                emptyList()
            }

            if (updates.isNotEmpty()) {
                changes = updates.toHistoryMemoryChanges(currentItems)
                currentItems = runCatching {
                    memory.applyUpdates(updates)
                }.getOrElse { exception ->
                    if (exception is CancellationException) {
                        throw exception
                    }
                    errors += formatMemoryError(exception)
                    currentItems
                }
            }
        }

        val retrievalPlan = runCatching {
            val retrievalRequest = contextPlanner.memoryRetrievalRequest(
                userPrompt = request.prompt,
                availableMemory = currentItems.toChatMemoryItems(),
            )
            sendCompletion(
                request = request,
                contextMessages = emptyList(),
                includeSystemPrompt = false,
                servicePrompt = retrievalRequest.prompt,
            ).takeUnless { it.isError }
                ?.content
                ?.toChatMemoryRetrievalPlan(json)
        }.getOrElse { exception ->
            if (exception is CancellationException) {
                throw exception
            }
            errors += formatMemoryError(exception)
            null
        }

        val injection = contextPlanner.memoryInjection(
            originalSystemPrompt = request.systemPrompt,
            retrievalPlan = retrievalPlan ?: com.sibgear.deepseek.chat.domain.model.ChatMemoryRetrievalPlan(),
            availableMemory = currentItems.toChatMemoryItems(),
        )

        return PreparedMemory(
            effectiveSystemPrompt = injection.effectiveSystemPrompt,
            metadata = HistoryMessageMemoryMetadata(
                storedLayers = changes.map { it.layer }.distinct(),
                usedLayers = injection.usedLayers.toHistoryMemoryLayers(),
                changes = changes,
                injectedItems = injection.injectedItems.toHistoryMemoryItems(),
                error = errors.joinToString(separator = "; ").takeIf { it.isNotBlank() },
            ),
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

private data class PreparedMemory(
    val effectiveSystemPrompt: String,
    val metadata: HistoryMessageMemoryMetadata?,
)

private fun formatMemoryError(exception: Throwable): String =
    "memory: ${exception.message ?: exception::class.simpleName ?: "unknown"}"

private const val ChatCompletionsUrl = "https://api.deepseek.com/chat/completions"
private const val ConnectTimeoutMillis = 30_000L
private const val RequestTimeoutMillis = 180_000L
