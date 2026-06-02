package com.sibgear.deepseek.ui

import com.sibgear.deepseek.domain.DeepSeekModel

sealed interface DeepSeekViewEvent {
    data class PromptChanged(val prompt: String) : DeepSeekViewEvent
    data class ApiKeyChanged(val apiKey: String) : DeepSeekViewEvent
    data class ModelSelected(val model: DeepSeekModel) : DeepSeekViewEvent
    data class ModelMenuExpandedChanged(val isExpanded: Boolean) : DeepSeekViewEvent
    data object SendClicked : DeepSeekViewEvent
}
