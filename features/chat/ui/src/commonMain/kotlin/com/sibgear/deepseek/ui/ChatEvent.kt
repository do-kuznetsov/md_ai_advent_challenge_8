package com.sibgear.deepseek.ui

import com.sibgear.deepseek.domain.AiModel

sealed interface ChatEvent {
    data class SystemPromptChanged(val systemPrompt: String) : ChatEvent
    data class PromptChanged(val prompt: String) : ChatEvent
    data class ModelFilterChanged(val filter: String) : ChatEvent
    data class ModelSelected(val model: AiModel) : ChatEvent
    data class ModelMenuExpandedChanged(val isExpanded: Boolean) : ChatEvent
    data class TemperatureChanged(val temperature: Float) : ChatEvent
    data class MaxTokensChanged(val maxTokens: String) : ChatEvent
    data class StopWordChanged(val stopWord: String) : ChatEvent
    data class ApiControlChanged(val isEnabled: Boolean) : ChatEvent
    data object SendClicked : ChatEvent
}
