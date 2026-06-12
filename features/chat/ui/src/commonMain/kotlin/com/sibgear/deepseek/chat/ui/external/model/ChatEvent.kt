package com.sibgear.deepseek.chat.ui.external.model

import com.sibgear.deepseek.chat.domain.model.AiModel
import com.sibgear.deepseek.chat.domain.model.PromptAttachment

sealed interface ChatEvent {
    data class SystemPromptChanged(val systemPrompt: String) : ChatEvent
    data class PromptChanged(val prompt: String) : ChatEvent
    data class AttachmentSelected(val attachment: PromptAttachment) : ChatEvent
    data class AttachmentError(val message: String) : ChatEvent
    data object AttachmentCleared : ChatEvent
    data class ModelFilterChanged(val filter: String) : ChatEvent
    data class ModelSelected(val model: AiModel) : ChatEvent
    data class ModelMenuExpandedChanged(val isExpanded: Boolean) : ChatEvent
    data class TemperatureChanged(val temperature: Float) : ChatEvent
    data class MaxTokensChanged(val maxTokens: String) : ChatEvent
    data class StopWordChanged(val stopWord: String) : ChatEvent
    data class ApiControlChanged(val isEnabled: Boolean) : ChatEvent
    data object SendClicked : ChatEvent
}
