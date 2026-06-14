package com.sibgear.deepseek.chat.ui.external.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sibgear.deepseek.chat.domain.model.AiProvider
import com.sibgear.deepseek.chat.domain.interactor.ChatInteractor
import com.sibgear.deepseek.chat.domain.model.ChatMessage
import com.sibgear.deepseek.chat.domain.model.ChatMessageAttachment
import com.sibgear.deepseek.chat.domain.model.ChatRole
import com.sibgear.deepseek.chat.domain.model.ContextManagementSettings
import com.sibgear.deepseek.chat.domain.model.DefaultContextManagementMessages
import com.sibgear.deepseek.chat.domain.model.AiModel
import com.sibgear.deepseek.chat.domain.model.AiRequestData
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
) {
    private var allOpenRouterModels: List<AiModel> = emptyList()

    var state by mutableStateOf(
        ChatViewState(
            messages = initialMessages,
            stickyFacts = initialStickyFacts,
        ),
    )
        private set

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
                state = state.copy(systemPrompt = event.systemPrompt)
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

    fun sendPrompt() {
        val prompt = state.prompt.trim()
        if (prompt.isEmpty() || state.isLoading) {
            return
        }

        val request = AiRequestData(
            systemPrompt = state.systemPrompt,
            prompt = prompt,
            attachment = state.attachment,
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
                val response = interactor.sendMessage(request)
                state = state.copy(
                    isLoading = false,
                    messages = response.messages,
                    stickyFacts = response.stickyFacts,
                    stickyFactsStatus = response.stickyFacts
                        .takeIf { it.isNotEmpty() }
                        ?.let { "facts: ${it.size}" },
                ).withContextPresentation()
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
            }
        }
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
