package com.sibgear.deepseek.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sibgear.deepseek.domain.AiProvider
import com.sibgear.deepseek.domain.ChatMessage
import com.sibgear.deepseek.domain.ChatRole
import com.sibgear.deepseek.domain.AiModel
import com.sibgear.deepseek.domain.DeepSeekModels
import com.sibgear.deepseek.domain.AiRepository
import com.sibgear.deepseek.domain.AiRequestData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: AiRepository,
    private val coroutineScope: CoroutineScope,
) {
    private var allOpenRouterModels: List<AiModel> = emptyList()

    var state by mutableStateOf(ChatViewState())
        private set

    fun onEvent(event: ChatEvent) {
        when (event) {
            is ChatEvent.ApiControlChanged -> {
                state = state.copy(
                    apiSettings = state.apiSettings.copy(
                        isApiControlEnabled = event.isEnabled,
                    ),
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
                )
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

    fun loadOpenRouterModels(apiKey: String) {
        coroutineScope.launch {
            state = state.copy(openRouterModelsStatus = "OpenRouter: загрузка моделей...")

            try {
                allOpenRouterModels = repository.loadOpenRouterModels(apiKey)
                applyOpenRouterFilter()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Throwable) {
                allOpenRouterModels = emptyList()
                state = state.copy(
                    openRouterModels = emptyList(),
                    selectedModel = DeepSeekModels.Default,
                    openRouterModelsStatus = "OpenRouter: ${exception.message ?: "ошибка загрузки моделей"}",
                )
            }
        }
    }

    fun sendPrompt(deepSeekApiKey: String, openRouterApiKey: String) {
        val prompt = state.prompt.trim()
        if (prompt.isEmpty() || state.isLoading) {
            return
        }

        val request = AiRequestData(
            deepSeekApiKey = deepSeekApiKey,
            openRouterApiKey = openRouterApiKey,
            systemPrompt = state.systemPrompt,
            prompt = prompt,
            model = state.selectedModel,
            apiSettings = state.apiSettings,
        )
        val userMessage = ChatMessage(role = ChatRole.User, content = prompt)

        coroutineScope.launch {
            state = state.copy(
                isLoading = true,
                prompt = "",
                messages = state.messages + userMessage,
            )

            try {
                val response = repository.sendMessage(request)
                state = state.copy(
                    isLoading = false,
                    messages = response.messages,
                )
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
                )
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
            DeepSeekModels.Default
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
        )
    }
}

private fun selectOpenRouterModels(
    models: List<AiModel>,
    filter: String,
): List<AiModel> {
    val words = filter.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
    val filteredModels = models.filter { model ->
        words.all { word -> model.searchText.contains(word, ignoreCase = true) }
    }

    return listOfNotNull(
        filteredModels.bestModel { it.modelSizeBillions?.let { size -> size in 7.0..14.0 } == true }
            ?.withTier(OpenRouterModelTier.Low),
        filteredModels.bestModel { it.modelSizeBillions?.let { size -> size in 30.0..70.0 } == true }
            ?.withTier(OpenRouterModelTier.Mid),
        filteredModels.bestModel {
            it.isReasoningModel || (it.isMixtureOfExperts && (it.modelSizeBillions ?: 0.0) >= 200.0)
        }?.withTier(OpenRouterModelTier.High),
    ).distinctBy { it.id }
}

private enum class OpenRouterModelTier(val label: String) {
    Low("low"),
    Mid("mid"),
    High("high"),
}

private fun AiModel.withTier(tier: OpenRouterModelTier): AiModel =
    copy(displayName = "[${tier.label}] $displayName")

private fun List<AiModel>.bestModel(predicate: (AiModel) -> Boolean): AiModel? =
    filter(predicate)
        .sortedWith(
            compareByDescending<AiModel> { it.isFreeNamed }
                .thenByDescending { it.contextLength ?: 0 }
                .thenBy { it.id },
        )
        .firstOrNull()

private val AiModel.searchText: String
    get() = "$id $displayName $description"

private val AiModel.isFreeNamed: Boolean
    get() = "$id $displayName".contains("free", ignoreCase = true)

private val AiModel.isMixtureOfExperts: Boolean
    get() {
        val text = searchText
        return text.contains("moe", ignoreCase = true) ||
            text.contains("mixture-of-experts", ignoreCase = true) ||
            text.contains("experts", ignoreCase = true)
    }

private val AiModel.isReasoningModel: Boolean
    get() {
        val text = searchText
        return supportedParameters.any { it.equals("reasoning", ignoreCase = true) } ||
            text.contains("reasoning", ignoreCase = true) ||
            text.contains("reasoner", ignoreCase = true) ||
            text.contains("thinking", ignoreCase = true) ||
            Regex("(^|[^a-z0-9])r1([^a-z0-9]|$)", RegexOption.IGNORE_CASE).containsMatchIn(text)
    }

private val AiModel.modelSizeBillions: Double?
    get() {
        val text = searchText
        val moeSizes = Regex("""(\d+(?:\.\d+)?)\s*x\s*(\d+(?:\.\d+)?)\s*b""", RegexOption.IGNORE_CASE)
            .findAll(text)
            .map { match -> match.groupValues[1].toDouble() * match.groupValues[2].toDouble() }
        val sizes = Regex("""(\d+(?:\.\d+)?)\s*(?:b|billion)""", RegexOption.IGNORE_CASE)
            .findAll(text)
            .map { match -> match.groupValues[1].toDouble() }

        return (moeSizes + sizes).maxOrNull()
    }
