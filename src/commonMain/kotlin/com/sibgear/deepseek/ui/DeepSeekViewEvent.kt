package com.sibgear.deepseek.ui

import com.sibgear.deepseek.domain.DeepSeekModel

sealed interface DeepSeekViewEvent {
    data class PromptChanged(val prompt: String) : DeepSeekViewEvent
    data class ApiKeyChanged(val apiKey: String) : DeepSeekViewEvent
    data class ModelSelected(val model: DeepSeekModel) : DeepSeekViewEvent
    data class ModelMenuExpandedChanged(val isExpanded: Boolean) : DeepSeekViewEvent
    data class TemperatureChanged(val temperature: Float) : DeepSeekViewEvent
    data class MaxTokensChanged(val maxTokens: String) : DeepSeekViewEvent
    data class StopWordChanged(val stopWord: String) : DeepSeekViewEvent
    data class ApiControlChanged(val isEnabled: Boolean) : DeepSeekViewEvent
    data object SendClicked : DeepSeekViewEvent
}
