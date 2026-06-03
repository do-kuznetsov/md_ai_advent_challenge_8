package com.sibgear.deepseek.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sibgear.deepseek.domain.ChatMessage
import com.sibgear.deepseek.domain.ChatRole
import com.sibgear.deepseek.domain.DeepSeekRepository
import com.sibgear.deepseek.domain.DeepSeekRequestData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class DeepSeekViewModel(
    private val repository: DeepSeekRepository,
    private val coroutineScope: CoroutineScope,
) {
    var state by mutableStateOf(DeepSeekViewState())
        private set

    fun onEvent(event: DeepSeekViewEvent) {
        when (event) {
            is DeepSeekViewEvent.ApiControlChanged -> {
                state = state.copy(
                    apiSettings = state.apiSettings.copy(
                        isApiControlEnabled = event.isEnabled,
                    ),
                )
            }

            is DeepSeekViewEvent.ApiKeyChanged -> {
                state = state.copy(apiKey = event.apiKey)
            }

            is DeepSeekViewEvent.MaxTokensChanged -> {
                val digitsOnly = event.maxTokens.filter { it.isDigit() }
                state = state.copy(
                    maxTokensInput = digitsOnly,
                    apiSettings = state.apiSettings.copy(
                        maxTokens = digitsOnly.toIntOrNull() ?: 0,
                    ),
                )
            }

            is DeepSeekViewEvent.ModelMenuExpandedChanged -> {
                state = state.copy(isModelMenuExpanded = event.isExpanded)
            }

            is DeepSeekViewEvent.ModelSelected -> {
                state = state.copy(
                    selectedModel = event.model,
                    isModelMenuExpanded = false,
                )
            }

            is DeepSeekViewEvent.PromptChanged -> {
                state = state.copy(prompt = event.prompt)
            }

            is DeepSeekViewEvent.StopWordChanged -> {
                state = state.copy(
                    apiSettings = state.apiSettings.copy(stopWord = event.stopWord),
                )
            }

            is DeepSeekViewEvent.SystemPromptChanged -> {
                state = state.copy(systemPrompt = event.systemPrompt)
            }

            is DeepSeekViewEvent.TemperatureChanged -> {
                state = state.copy(
                    apiSettings = state.apiSettings.copy(
                        temperature = event.temperature.coerceIn(0f, 1f),
                    ),
                )
            }

            DeepSeekViewEvent.SendClicked -> sendPrompt()
        }
    }

    private fun sendPrompt() {
        if (!state.isSendEnabled) {
            return
        }

        val request = DeepSeekRequestData(
            apiKey = state.apiKey,
            systemPrompt = state.systemPrompt,
            prompt = state.prompt,
            model = state.selectedModel,
            apiSettings = state.apiSettings,
        )
        val userMessage = ChatMessage(role = ChatRole.User, content = state.prompt)

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
}
