package com.sibgear.deepseek.chat.ui.external.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sibgear.deepseek.chat.domain.model.AiProvider
import com.sibgear.deepseek.chat.domain.interactor.ChatInteractor
import com.sibgear.deepseek.chat.domain.interactor.TaskMemoryStateUpdater
import com.sibgear.deepseek.chat.domain.model.ApiSettings
import com.sibgear.deepseek.chat.domain.model.ChatMessage
import com.sibgear.deepseek.chat.domain.model.ChatMessageAttachment
import com.sibgear.deepseek.chat.domain.model.ChatBranch
import com.sibgear.deepseek.chat.domain.model.ChatMessageKind
import com.sibgear.deepseek.chat.domain.model.ChatRole
import com.sibgear.deepseek.chat.domain.model.ContextManagementSettings
import com.sibgear.deepseek.chat.domain.model.DefaultContextManagementMessages
import com.sibgear.deepseek.chat.domain.model.AiModel
import com.sibgear.deepseek.chat.domain.model.AiToolProvider
import com.sibgear.deepseek.chat.domain.model.AiRequestData
import com.sibgear.deepseek.chat.domain.model.PromptAttachment
import com.sibgear.deepseek.chat.domain.model.StickyFact
import com.sibgear.deepseek.chat.domain.model.TaskMemoryState
import com.sibgear.deepseek.chat.domain.model.userApiContent
import com.sibgear.deepseek.chat.ui.external.model.ChatEvent
import com.sibgear.deepseek.chat.ui.external.model.ChatViewState
import com.sibgear.deepseek.chat.ui.internal.mapper.buildContextUsageLabel
import com.sibgear.deepseek.chat.ui.internal.mapper.buildPinnedContextMessageIndex
import com.sibgear.deepseek.chat.ui.internal.mapper.selectOpenRouterModels
import com.sibgear.deepseek.chat.ui.internal.model.ChatDefaults
import com.sibgear.rag.domain.interactor.RagQueryInteractor
import com.sibgear.rag.domain.model.RagQuery
import com.sibgear.rag.domain.model.RagQueryResult
import com.sibgear.rag.domain.model.RagRetrievalConfig
import com.sibgear.rag.domain.model.RagSearchResult
import com.sibgear.rag.domain.repository.RagReranker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ChatViewModel(
    private val interactor: ChatInteractor,
    private val coroutineScope: CoroutineScope,
    initialMessages: List<ChatMessage> = emptyList(),
    initialStickyFacts: List<StickyFact> = emptyList(),
    initialBranches: List<ChatBranch> = emptyList(),
    initialSystemPrompt: String = "",
    initialPrompt: String = "",
    isSystemPromptReadOnly: Boolean = false,
    private val toolProvider: AiToolProvider? = null,
    private val ragQueryInteractor: RagQueryInteractor? = null,
    private val ragRerankerFactory: ((String) -> RagReranker)? = null,
    private val taskMemoryStateUpdater: TaskMemoryStateUpdater = TaskMemoryStateUpdater(),
    private val persistMessage: (suspend (ChatMessage) -> List<ChatMessage>)? = null,
) {
    private var allOpenRouterModels: List<AiModel> = emptyList()

    var state by mutableStateOf(
        ChatViewState(
            systemPrompt = initialSystemPrompt,
            isSystemPromptReadOnly = isSystemPromptReadOnly,
            prompt = initialPrompt,
            messages = initialMessages,
            stickyFacts = initialStickyFacts,
            branches = initialBranches,
            activeBranchId = initialMessages.lastOrNull { it.branchId != null }?.branchId,
            taskMemoryState = taskMemoryStateUpdater.replay(initialMessages),
        ),
    )
        private set

    val selectedModelProviderName: String
        get() = state.selectedModel.provider.name

    val selectedModelId: String
        get() = state.selectedModel.id

    fun onEvent(event: ChatEvent) {
        when (event) {
            ChatEvent.AttachmentCleared -> {
                state = state.copy(
                    attachment = null,
                    attachmentError = null,
                )
            }

            is ChatEvent.AttachmentError -> {
                state = state.copy(attachmentError = event.message)
            }

            is ChatEvent.AttachmentSelected -> {
                state = state.copy(
                    attachment = event.attachment,
                    attachmentError = null,
                )
            }

            is ChatEvent.ApiControlChanged -> {
                state = state.copy(
                    apiSettings = state.apiSettings.copy(
                        isApiControlEnabled = event.isEnabled,
                    ),
                )
            }

            is ChatEvent.RagEnabledChanged -> {
                state = state.copy(
                    isRagEnabled = event.isEnabled,
                    ragStatus = if (event.isEnabled) state.ragStatus else null,
                )
            }

            is ChatEvent.RagStrategySelected -> {
                state = state.copy(
                    ragStrategy = event.strategy,
                    ragStatus = null,
                )
            }

            is ChatEvent.RagIndexDirectoryChanged -> {
                state = state.copy(
                    ragIndexDirectory = event.indexDirectory,
                    ragStatus = null,
                )
            }

            is ChatEvent.RagFilteringEnabledChanged -> {
                state = state.copy(
                    isRagFilteringEnabled = event.isEnabled,
                    ragStatus = null,
                )
            }

            is ChatEvent.RagTopKBeforeFilterChanged -> {
                state = state.copy(
                    ragTopKBeforeFilterInput = event.topK.filter { it.isDigit() },
                    ragStatus = null,
                )
            }

            is ChatEvent.RagTopKAfterFilterChanged -> {
                state = state.copy(
                    ragTopKAfterFilterInput = event.topK.filter { it.isDigit() },
                    ragStatus = null,
                )
            }

            is ChatEvent.RagSimilarityThresholdChanged -> {
                state = state.copy(
                    ragSimilarityThresholdInput = event.threshold.filter { it.isDigit() || it == '.' },
                    ragStatus = null,
                )
            }

            is ChatEvent.RagQueryRewriteEnabledChanged -> {
                state = state.copy(
                    isRagQueryRewriteEnabled = event.isEnabled,
                    ragStatus = null,
                )
            }

            is ChatEvent.RagRerankingEnabledChanged -> {
                state = state.copy(
                    isRagRerankingEnabled = event.isEnabled,
                    ragStatus = null,
                )
            }

            is ChatEvent.RagRerankerModelDirectoryChanged -> {
                state = state.copy(
                    ragRerankerModelDirectory = event.modelDirectory,
                    ragStatus = null,
                )
            }

            is ChatEvent.ContextManagementModeSelected -> {
                state = state.copy(contextManagementMode = event.mode).withContextPresentation()
            }

            is ChatEvent.ContextManagementPanelExpandedChanged -> {
                state = state.copy(isContextManagementPanelExpanded = event.isExpanded)
            }

            is ChatEvent.SummaryIntervalChanged -> {
                state = state.copy(summaryIntervalInput = event.interval.filter { it.isDigit() })
                    .withContextPresentation()
            }

            is ChatEvent.SlidingWindowMessagesChanged -> {
                state = state.copy(slidingWindowMessagesInput = event.messages.filter { it.isDigit() })
                    .withContextPresentation()
            }

            is ChatEvent.StickyFactsWindowMessagesChanged -> {
                state = state.copy(stickyFactsWindowInput = event.messages.filter { it.isDigit() })
                    .withContextPresentation()
            }

            is ChatEvent.CompressionSummaryToggled -> {
                state = state.copy(
                    expandedCompressionMessageIndexes = state.expandedCompressionMessageIndexes.toggle(event.messageIndex),
                )
            }

            is ChatEvent.MaxTokensChanged -> {
                val digitsOnly = event.maxTokens.filter { it.isDigit() }
                state = state.copy(
                    maxTokensInput = digitsOnly,
                    apiSettings = state.apiSettings.copy(
                        maxTokens = digitsOnly.toIntOrNull() ?: 0,
                    ),
                )
            }

            is ChatEvent.ModelFilterChanged -> {
                state = state.copy(modelFilter = event.filter)
                applyOpenRouterFilter()
            }

            is ChatEvent.ModelMenuExpandedChanged -> {
                state = state.copy(isModelMenuExpanded = event.isExpanded)
            }

            is ChatEvent.ModelSelected -> {
                state = state.copy(
                    selectedModel = event.model,
                    isModelMenuExpanded = false,
                ).withContextPresentation()
            }

            is ChatEvent.PromptChanged -> {
                state = state.copy(prompt = event.prompt)
            }

            is ChatEvent.StopWordChanged -> {
                state = state.copy(
                    apiSettings = state.apiSettings.copy(stopWord = event.stopWord),
                )
            }

            is ChatEvent.SystemPromptChanged -> {
                if (!state.isSystemPromptReadOnly && state.messages.isEmpty()) {
                    state = state.copy(systemPrompt = event.systemPrompt)
                }
            }

            is ChatEvent.TemperatureChanged -> {
                state = state.copy(
                    apiSettings = state.apiSettings.copy(
                        temperature = event.temperature.coerceIn(0f, 1f),
                    ),
                )
            }

            ChatEvent.SendClicked -> Unit
        }
    }

    fun loadModels() {
        coroutineScope.launch {
            state = state.copy(
                magnitCopilotModelsStatus = "MCopilot: загрузка моделей...",
                openRouterModelsStatus = "OpenRouter: загрузка моделей...",
            )

            val deepSeekModels = runCatching {
                interactor.loadModels(AiProvider.DeepSeek)
            }.getOrDefault(emptyList())
                .takeIf { it.isNotEmpty() }
                ?: listOf(ChatDefaults.DefaultDeepSeekModel)

            state = state.copy(
                deepSeekModels = deepSeekModels,
                selectedModel = if (state.selectedModel.provider == AiProvider.DeepSeek) {
                    state.selectedModel.takeIf { selectedModel ->
                        deepSeekModels.any { it.provider == selectedModel.provider && it.id == selectedModel.id }
                    } ?: deepSeekFallback(deepSeekModels)
                } else {
                    state.selectedModel
                },
            ).withContextPresentation()

            val magnitCopilotModels = runCatching {
                interactor.loadModels(AiProvider.MagnitCopilot)
            }.getOrDefault(emptyList())
            state = state.copy(
                magnitCopilotModels = magnitCopilotModels,
                selectedModel = if (state.selectedModel.provider == AiProvider.MagnitCopilot &&
                    magnitCopilotModels.none { it.id == state.selectedModel.id }
                ) {
                    deepSeekFallback(state.deepSeekModels)
                } else {
                    state.selectedModel
                },
                magnitCopilotModelsStatus = when {
                    magnitCopilotModels.isEmpty() -> "MCopilot: нет моделей"
                    else -> "MCopilot: ${magnitCopilotModels.size} моделей"
                },
            ).withContextPresentation()

            try {
                allOpenRouterModels = interactor.loadModels(AiProvider.OpenRouter)
                applyOpenRouterFilter()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Throwable) {
                allOpenRouterModels = emptyList()
                state = state.copy(
                    openRouterModels = emptyList(),
                    selectedModel = if (state.selectedModel.provider == AiProvider.OpenRouter) {
                        deepSeekFallback(state.deepSeekModels)
                    } else {
                        state.selectedModel
                    },
                    openRouterModelsStatus = "OpenRouter: ${exception.message ?: "ошибка загрузки моделей"}",
                ).withContextPresentation()
            }
        }
    }

    fun sendPrompt(
        runtimeSystemPrompt: String? = null,
        onCompleted: ((ChatViewState) -> Unit)? = null,
    ) {
        val prompt = state.prompt.trim()
        if (prompt.isEmpty() || state.isLoading) {
            return
        }

        val ragSettings = state.toRagRequestSettings()
        val request = buildRequest(
            prompt = prompt,
            runtimeSystemPrompt = runtimeSystemPrompt,
            attachment = state.attachment,
            persistUserMessage = true,
        )
        val userMessage = ChatMessage(
            role = ChatRole.User,
            content = prompt,
            apiContent = request.userApiContent(),
            attachment = request.attachment?.let {
                ChatMessageAttachment(
                    fileName = it.fileName,
                    sizeBytes = it.sizeBytes,
                )
            },
        )

        val taskMemoryState = taskMemoryStateUpdater.update(state.taskMemoryState, prompt)

        coroutineScope.launch {
            state = state.copy(
                isLoading = true,
                prompt = "",
                attachment = null,
                attachmentError = null,
                messages = state.messages + userMessage,
                taskMemoryState = taskMemoryState,
            ).withContextPresentation()

            try {
                val messagesBeforeRagCount = state.messages.size
                val preparedRequest = try {
                    request.withRagContextIfNeeded(prompt, ragSettings, taskMemoryState)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: RagNoRelevantContextException) {
                    state = state.copy(
                        isLoading = false,
                        messages = state.messages + ChatMessage(
                            role = ChatRole.Assistant,
                            content = exception.message ?: RagNoRelevantContextMessage,
                        ),
                        ragStatus = exception.ragStatus,
                    ).withContextPresentation()
                    onCompleted?.invoke(state)
                    return@launch
                } catch (exception: Throwable) {
                    state = state.copy(
                        isLoading = false,
                        messages = state.messages + ChatMessage(
                            role = ChatRole.Assistant,
                            content = "Ошибка RAG: ${exception.message ?: exception::class.simpleName ?: "unknown"}",
                        ),
                        ragStatus = "RAG: ошибка",
                    ).withContextPresentation()
                    onCompleted?.invoke(state)
                    return@launch
                }
                val ragDiagnostics = state.messages
                    .drop(messagesBeforeRagCount)
                    .filter { it.kind == ChatMessageKind.RagDiagnostic }
                sendRequestAndUpdateState(preparedRequest, onCompleted, ragDiagnostics)
            } catch (exception: CancellationException) {
                state = state.copy(isLoading = false)
                throw exception
            } catch (exception: Throwable) {
                val errorMessage = ChatMessage(
                    role = ChatRole.Assistant,
                    content = "Ошибка запроса: ${exception.message ?: exception::class.simpleName ?: "unknown"}",
                )
                state = state.copy(
                    isLoading = false,
                    messages = state.messages + errorMessage,
                ).withContextPresentation()
                onCompleted?.invoke(state)
            }
        }
    }

    fun sendSyntheticPrompt(
        prompt: String,
        runtimeSystemPrompt: String? = null,
        onCompleted: ((ChatViewState) -> Unit)? = null,
    ) {
        val trimmedPrompt = prompt.trim()
        if (trimmedPrompt.isEmpty() || state.isLoading) {
            return
        }

        val request = buildRequest(
            prompt = trimmedPrompt,
            runtimeSystemPrompt = runtimeSystemPrompt,
            attachment = null,
            persistUserMessage = false,
        )

        coroutineScope.launch {
            state = state.copy(
                isLoading = true,
                attachmentError = null,
            ).withContextPresentation()

            try {
                sendRequestAndUpdateState(request, onCompleted)
            } catch (exception: CancellationException) {
                state = state.copy(isLoading = false)
                throw exception
            } catch (exception: Throwable) {
                val errorMessage = ChatMessage(
                    role = ChatRole.Assistant,
                    content = "Ошибка запроса: ${exception.message ?: exception::class.simpleName ?: "unknown"}",
                )
                state = state.copy(
                    isLoading = false,
                    messages = state.messages + errorMessage,
                ).withContextPresentation()
                onCompleted?.invoke(state)
            }
        }
    }

    fun setPrompt(prompt: String) {
        state = state.copy(prompt = prompt)
    }

    fun appendLocalMessage(message: ChatMessage) {
        state = state.copy(
            messages = state.messages + message,
            taskMemoryState = state.taskMemoryState.updatedWith(message, taskMemoryStateUpdater),
        ).withContextPresentation()
    }

    fun appendPersistentMessage(
        message: ChatMessage,
        onCompleted: ((ChatViewState) -> Unit)? = null,
    ) {
        val persist = persistMessage
        if (persist == null) {
            appendLocalMessage(message)
            onCompleted?.invoke(state)
            return
        }

        coroutineScope.launch {
            try {
                val persistedMessages = persist(message)
                state = state.copy(
                    messages = persistedMessages,
                    taskMemoryState = taskMemoryStateUpdater.replay(persistedMessages),
                ).withContextPresentation()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Throwable) {
                state = state.copy(
                    messages = state.messages + message,
                    taskMemoryState = state.taskMemoryState.updatedWith(message, taskMemoryStateUpdater),
                ).withContextPresentation()
            }
            onCompleted?.invoke(state)
        }
    }

    fun syncRequestSettingsFrom(source: ChatViewState) {
        state = state.copy(
            selectedModel = source.selectedModel,
            openRouterModels = source.openRouterModels,
            deepSeekModels = source.deepSeekModels,
            modelFilter = source.modelFilter,
            openRouterModelsStatus = source.openRouterModelsStatus,
            contextManagementMode = source.contextManagementMode,
            summaryIntervalInput = source.summaryIntervalInput,
            slidingWindowMessagesInput = source.slidingWindowMessagesInput,
            stickyFactsWindowInput = source.stickyFactsWindowInput,
            apiSettings = source.apiSettings,
            maxTokensInput = source.maxTokensInput,
            isRagEnabled = source.isRagEnabled,
            ragStrategy = source.ragStrategy,
            ragIndexDirectory = source.ragIndexDirectory,
            isRagFilteringEnabled = source.isRagFilteringEnabled,
            ragTopKBeforeFilterInput = source.ragTopKBeforeFilterInput,
            ragTopKAfterFilterInput = source.ragTopKAfterFilterInput,
            ragSimilarityThresholdInput = source.ragSimilarityThresholdInput,
            isRagQueryRewriteEnabled = source.isRagQueryRewriteEnabled,
            isRagRerankingEnabled = source.isRagRerankingEnabled,
            ragRerankerModelDirectory = source.ragRerankerModelDirectory,
            ragStatus = source.ragStatus,
        ).withContextPresentation()
    }

    private fun buildRequest(
        prompt: String,
        runtimeSystemPrompt: String?,
        attachment: PromptAttachment?,
        persistUserMessage: Boolean,
    ): AiRequestData =
        AiRequestData(
            systemPrompt = state.systemPrompt.withRuntimeSystemPrompt(runtimeSystemPrompt),
            prompt = prompt,
            attachment = attachment,
            model = state.selectedModel,
            apiSettings = state.apiSettings,
            contextManagementSettings = ContextManagementSettings(
                mode = state.contextManagementMode,
                summaryIntervalMessages = state.summaryIntervalInput.toIntOrNull()
                    ?.coerceAtLeast(1)
                    ?: DefaultContextManagementMessages,
                slidingWindowMessages = state.slidingWindowMessagesInput.toIntOrNull()
                    ?.coerceAtLeast(1)
                    ?: DefaultContextManagementMessages,
                stickyFactsWindowMessages = state.stickyFactsWindowInput.toIntOrNull()
                    ?.coerceAtLeast(1)
                    ?: DefaultContextManagementMessages,
            ),
            persistUserMessage = persistUserMessage,
            toolProvider = toolProvider,
        )

    private suspend fun AiRequestData.withRagContextIfNeeded(
        originalPrompt: String,
        ragSettings: RagRequestSettings,
        taskMemoryState: TaskMemoryState,
    ): AiRequestData {
        if (!ragSettings.isEnabled) {
            return this
        }

        val ragInteractor = requireNotNull(ragQueryInteractor) {
            "RAG не настроен в приложении."
        }
        val memoryAwarePrompt = taskMemoryState.toRagQueryText(originalPrompt)
        val rewrittenPrompt = if (ragSettings.isQueryRewriteEnabled) {
            rewriteRagQuery(memoryAwarePrompt).also { rewritten ->
                appendRagDiagnostic(originalPrompt, rewritten)
            }
        } else {
            null
        }
        val searchQuestion = rewrittenPrompt ?: memoryAwarePrompt
        val reranker = if (ragSettings.retrievalConfig.isRerankingEnabled) {
            requireNotNull(ragRerankerFactory) {
                "RAG reranker не настроен в приложении."
            }.invoke(ragSettings.rerankerModelDirectory)
        } else {
            null
        }
        val result = ragInteractor.search(
            RagQuery(
                strategy = ragSettings.strategy,
                indexDirectory = ragSettings.indexDirectory,
                question = searchQuestion,
                retrievalConfig = ragSettings.retrievalConfig,
                rewrittenQuestion = rewrittenPrompt,
            ),
            reranker = reranker,
        )
        if (result.results.isEmpty()) {
            throw RagNoRelevantContextException(
                ragStatus = result.toStatus(ragSettings),
            )
        }

        state = state.copy(
            ragStatus = result.toStatus(ragSettings),
        ).withContextPresentation()

        return copy(
            systemPrompt = systemPrompt.withRagContext(result.results, taskMemoryState),
            prompt = originalPrompt,
        )
    }

    private suspend fun rewriteRagQuery(originalPrompt: String): String {
        val response = interactor.sendMessage(
            AiRequestData(
                systemPrompt = RagRewriteSystemPrompt,
                prompt = originalPrompt,
                attachment = null,
                model = state.selectedModel,
                apiSettings = RagRewriteApiSettings,
                contextManagementSettings = ContextManagementSettings(),
                persistUserMessage = false,
                toolProvider = null,
            ),
        )
        return response.messages
            .lastOrNull { it.role == ChatRole.Assistant }
            ?.content
            ?.lineSequence()
            ?.firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.removeSurrounding("\"")
            ?.takeIf { it.isNotBlank() }
            ?: error("query rewrite failed: empty response")
    }

    private fun appendRagDiagnostic(
        originalPrompt: String,
        rewrittenPrompt: String,
    ) {
        state = state.copy(
            messages = state.messages + ChatMessage(
                role = ChatRole.Assistant,
                kind = ChatMessageKind.RagDiagnostic,
                content = buildString {
                    appendLine("RAG rewrite:")
                    appendLine("original: $originalPrompt")
                    append("rewritten: $rewrittenPrompt")
                },
            ),
        ).withContextPresentation()
    }

    private suspend fun sendRequestAndUpdateState(
        request: AiRequestData,
        onCompleted: ((ChatViewState) -> Unit)?,
        preservedLocalMessages: List<ChatMessage> = emptyList(),
    ) {
        val response = interactor.sendMessage(request)
        state = state.copy(
            isLoading = false,
            messages = response.messages.withPreservedLocalMessages(preservedLocalMessages),
            stickyFacts = response.stickyFacts,
            stickyFactsStatus = response.stickyFacts
                .takeIf { it.isNotEmpty() }
                ?.let { "facts: ${it.size}" },
            branches = response.branches,
            activeBranchId = response.activeBranchId,
            branchingStatus = response.activeBranchId?.let { "branch: $it" },
        ).withContextPresentation()
        onCompleted?.invoke(state)
    }

    private fun applyOpenRouterFilter() {
        val openRouterModels = selectOpenRouterModels(
            models = allOpenRouterModels,
            filter = state.modelFilter,
        )
        val selectedModel = if (state.selectedModel.provider == AiProvider.OpenRouter &&
            openRouterModels.none { it.id == state.selectedModel.id }
        ) {
            deepSeekFallback(state.deepSeekModels)
        } else {
            state.selectedModel
        }
        val status = when {
            allOpenRouterModels.isEmpty() -> state.openRouterModelsStatus
            openRouterModels.isEmpty() -> "OpenRouter: нет моделей по фильтру"
            else -> null
        }

        state = state.copy(
            openRouterModels = openRouterModels,
            selectedModel = selectedModel,
            openRouterModelsStatus = status,
        ).withContextPresentation()
    }

    private fun deepSeekFallback(models: List<AiModel>): AiModel =
        models.firstOrNull { it.id == ChatDefaults.DefaultDeepSeekModel.id } ?: ChatDefaults.DefaultDeepSeekModel

    private fun ChatViewState.withContextPresentation(): ChatViewState =
        copy(
            contextUsageLabel = buildContextUsageLabel(messages, selectedModel),
            pinnedContextMessageIndex = buildPinnedContextMessageIndex(
                messages = messages,
                mode = contextManagementMode,
                slidingWindowMessagesInput = slidingWindowMessagesInput,
            ),
        )
}

private data class RagRequestSettings(
    val isEnabled: Boolean,
    val strategy: com.sibgear.rag.domain.model.ChunkingStrategyType,
    val indexDirectory: String,
    val retrievalConfig: RagRetrievalConfig,
    val isQueryRewriteEnabled: Boolean,
    val rerankerModelDirectory: String,
)

private fun ChatViewState.toRagRequestSettings(): RagRequestSettings {
    val topKAfterFilter = ragTopKAfterFilterInput.toIntOrNull()
        ?.coerceAtLeast(1)
        ?: DefaultRagTopKAfterFilter
    val topKBeforeFilter = ragTopKBeforeFilterInput.toIntOrNull()
        ?.coerceAtLeast(1)
        ?.coerceAtLeast(topKAfterFilter)
        ?: DefaultRagTopKBeforeFilter.coerceAtLeast(topKAfterFilter)
    return RagRequestSettings(
        isEnabled = isRagEnabled,
        strategy = ragStrategy,
        indexDirectory = ragIndexDirectory.trim().ifBlank { DefaultRagIndexDirectory },
        retrievalConfig = RagRetrievalConfig(
            topKBeforeFilter = topKBeforeFilter,
            topKAfterFilter = topKAfterFilter,
            similarityThreshold = ragSimilarityThresholdInput.toFloatOrNull()?.coerceIn(0f, 1f)
                ?: DefaultRagSimilarityThreshold,
            isFilteringEnabled = isRagFilteringEnabled,
            isRerankingEnabled = isRagRerankingEnabled,
        ),
        isQueryRewriteEnabled = isRagQueryRewriteEnabled,
        rerankerModelDirectory = ragRerankerModelDirectory.trim().ifBlank { DefaultRagRerankerModelDirectory },
    )
}

private fun RagQueryResult.toStatus(settings: RagRequestSettings): String =
    "RAG: ${settings.strategy.cliName}; " +
        "rewrite ${settings.isQueryRewriteEnabled.onOff()}; " +
        "filter ${settings.retrievalConfig.isFilteringEnabled.onOff()}; " +
        "rerank ${settings.retrievalConfig.isRerankingEnabled.onOff()}; " +
        "${rawResultsCount}->${filteredResultsCount}->${rerankedResultsCount} chunks; " +
        "threshold ${settings.retrievalConfig.similarityThreshold}; " +
        "topK ${settings.retrievalConfig.topKBeforeFilter}/${settings.retrievalConfig.topKAfterFilter}"

private fun List<ChatMessage>.withPreservedLocalMessages(
    preservedLocalMessages: List<ChatMessage>,
): List<ChatMessage> {
    val missingMessages = preservedLocalMessages.filterNot { preserved ->
        any { it.kind == preserved.kind && it.content == preserved.content }
    }
    if (missingMessages.isEmpty()) {
        return this
    }

    val lastAssistantIndex = indexOfLast {
        it.role == ChatRole.Assistant && it.kind == ChatMessageKind.Regular
    }
    return if (lastAssistantIndex == -1) {
        this + missingMessages
    } else {
        take(lastAssistantIndex) + missingMessages + drop(lastAssistantIndex)
    }
}

private fun Boolean.onOff(): String = if (this) "on" else "off"

private fun String.withRagContext(
    results: List<RagSearchResult>,
    taskMemoryState: TaskMemoryState,
): String =
    buildString {
        append(this@withRagContext)
        if (isNotBlank()) {
            appendLine()
            appendLine()
        }
        if (!taskMemoryState.isEmpty) {
            appendLine("[TASK_MEMORY]")
            appendLine("Используй эту память задачи, чтобы понимать follow-up вопросы пользователя.")
            appendLine("Факты из документации подтверждай только найденными RAG источниками.")
            taskMemoryState.goal?.takeIf { it.isNotBlank() }?.let {
                appendLine("goal: $it")
            }
            if (taskMemoryState.clarifiedFacts.isNotEmpty()) {
                appendLine("clarified_facts:")
                taskMemoryState.clarifiedFacts.forEach { appendLine("- $it") }
            }
            if (taskMemoryState.constraints.isNotEmpty()) {
                appendLine("constraints:")
                taskMemoryState.constraints.forEach { appendLine("- $it") }
            }
            if (taskMemoryState.terms.isNotEmpty()) {
                appendLine("terms:")
                taskMemoryState.terms.forEach { (term, meaning) ->
                    appendLine("- $term = $meaning")
                }
            }
            appendLine("[/TASK_MEMORY]")
            appendLine()
        }
        appendLine("[RAG_CONTEXT]")
        appendLine("Используй только этот контекст для ответа на вопрос пользователя.")
        appendLine("Не используй внешние знания для фактов из документации.")
        appendLine("Ответ всегда верни в трех секциях: Ответ, Источники, Цитаты.")
        appendLine("В секции Источники перечисли использованные источники в формате: source | section | chunk_id.")
        appendLine("В секции Цитаты приведи дословные фрагменты из text найденных чанков.")
        appendLine("Если контекст не содержит ответа, напиши: Не знаю. Уточните вопрос.")
        results.forEachIndexed { index, result ->
            appendLine()
            appendLine("Chunk ${index + 1}")
            appendLine("[source=${result.source} section=${result.section} chunk_id=${result.chunkId}]")
            appendLine("source: ${result.source}")
            appendLine("title: ${result.title}")
            appendLine("section: ${result.section}")
            appendLine("chunk_id: ${result.chunkId}")
            appendLine("score: ${result.score}")
            result.rerankScore?.let { appendLine("rerank_score: $it") }
            result.rerankRawScore?.let { appendLine("rerank_raw_score: $it") }
            appendLine("text:")
            appendLine(result.text)
        }
        append("[/RAG_CONTEXT]")
    }

private class RagNoRelevantContextException(
    val ragStatus: String,
) : IllegalStateException(RagNoRelevantContextMessage)

private const val RagNoRelevantContextMessage =
    "Не знаю. В индексе не найден достаточно релевантный контекст. " +
        "Уточните вопрос или снизьте порог релевантности."
private const val DefaultRagIndexDirectory = "../rag/indexed"
private const val DefaultRagRerankerModelDirectory = "../rag/models/bge-reranker-v2-m3"
private const val DefaultRagTopKBeforeFilter = 15
private const val DefaultRagTopKAfterFilter = 5
private const val DefaultRagSimilarityThreshold = 0.7f

private val RagRewriteApiSettings = ApiSettings(
    temperature = 0f,
    maxTokens = 256,
    isApiControlEnabled = true,
)

private val RagRewriteSystemPrompt = """
    Перепиши вопрос пользователя для поиска по локальной технической документации.
    Сохрани важные имена модулей, файлов, классов, технологий и терминов.
    Не отвечай на вопрос.
    Верни только один переписанный поисковый запрос без пояснений.
""".trimIndent()

private fun TaskMemoryState.updatedWith(
    message: ChatMessage,
    updater: TaskMemoryStateUpdater,
): TaskMemoryState =
    if (message.role == ChatRole.User && message.kind == ChatMessageKind.Regular) {
        updater.update(this, message.content)
    } else {
        this
    }

private fun Set<Int>.toggle(value: Int): Set<Int> =
    if (value in this) this - value else this + value

private fun String.withRuntimeSystemPrompt(runtimeSystemPrompt: String?): String {
    val runtime = runtimeSystemPrompt?.trim().orEmpty()
    if (runtime.isEmpty()) {
        return this
    }

    val base = trim()
    return buildString {
        if (base.isNotEmpty()) {
            appendLine(base)
            appendLine()
        }
        append(runtime)
    }
}
