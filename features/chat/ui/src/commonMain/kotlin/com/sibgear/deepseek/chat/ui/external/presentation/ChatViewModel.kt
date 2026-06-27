package com.sibgear.deepseek.chat.ui.external.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sibgear.deepseek.chat.domain.model.AiProvider
import com.sibgear.deepseek.chat.domain.interactor.ChatInteractor
import com.sibgear.deepseek.chat.domain.model.ChatMessage
import com.sibgear.deepseek.chat.domain.model.ChatMessageAttachment
import com.sibgear.deepseek.chat.domain.model.ChatBranch
import com.sibgear.deepseek.chat.domain.model.ChatRole
import com.sibgear.deepseek.chat.domain.model.ContextManagementSettings
import com.sibgear.deepseek.chat.domain.model.DefaultContextManagementMessages
import com.sibgear.deepseek.chat.domain.model.AiModel
import com.sibgear.deepseek.chat.domain.model.AiToolProvider
import com.sibgear.deepseek.chat.domain.model.AiRequestData
import com.sibgear.deepseek.chat.domain.model.PromptAttachment
import com.sibgear.deepseek.chat.domain.model.StickyFact
import com.sibgear.deepseek.chat.domain.model.userApiContent
import com.sibgear.deepseek.chat.ui.external.model.ChatEvent
import com.sibgear.deepseek.chat.ui.external.model.ChatViewState
import com.sibgear.deepseek.chat.ui.internal.mapper.buildContextUsageLabel
import com.sibgear.deepseek.chat.ui.internal.mapper.buildPinnedContextMessageIndex
import com.sibgear.deepseek.chat.ui.internal.mapper.selectOpenRouterModels
import com.sibgear.deepseek.chat.ui.internal.model.ChatDefaults
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
            state = state.copy(openRouterModelsStatus = "OpenRouter: загрузка моделей...")

            val deepSeekModels = runCatching {
                interactor.loadModels(AiProvider.DeepSeek)
            }.getOrDefault(emptyList())
                .takeIf { it.isNotEmpty() }
                ?: listOf(ChatDefaults.DefaultModel)

            state = state.copy(
                deepSeekModels = deepSeekModels,
                selectedModel = state.selectedModel.takeIf { selectedModel ->
                    deepSeekModels.any { it.provider == selectedModel.provider && it.id == selectedModel.id }
                } ?: deepSeekModels.firstOrNull { it.id == ChatDefaults.DefaultModel.id } ?: ChatDefaults.DefaultModel,
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
                    selectedModel = state.deepSeekModels.firstOrNull { it.id == ChatDefaults.DefaultModel.id }
                        ?: ChatDefaults.DefaultModel,
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

        coroutineScope.launch {
            state = state.copy(
                isLoading = true,
                prompt = "",
                attachment = null,
                attachmentError = null,
                messages = state.messages + userMessage,
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
        state = state.copy(messages = state.messages + message).withContextPresentation()
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
                state = state.copy(messages = persist(message)).withContextPresentation()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Throwable) {
                state = state.copy(messages = state.messages + message).withContextPresentation()
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

    private suspend fun sendRequestAndUpdateState(
        request: AiRequestData,
        onCompleted: ((ChatViewState) -> Unit)?,
    ) {
        val response = interactor.sendMessage(request)
        state = state.copy(
            isLoading = false,
            messages = response.messages,
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
            state.deepSeekModels.firstOrNull { it.id == ChatDefaults.DefaultModel.id } ?: ChatDefaults.DefaultModel
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
