package com.sibgear.deepseek.chat.ui.external.model

import com.sibgear.deepseek.chat.domain.model.AiModel
import com.sibgear.deepseek.chat.domain.model.ContextManagementMode
import com.sibgear.deepseek.chat.domain.model.PromptAttachment
import com.sibgear.rag.domain.model.ChunkingStrategyType

sealed interface ChatEvent {
    data class SystemPromptChanged(val systemPrompt: String) : ChatEvent
    data class PromptChanged(val prompt: String) : ChatEvent
    data class AttachmentSelected(val attachment: PromptAttachment) : ChatEvent
    data class AttachmentError(val message: String) : ChatEvent
    data object AttachmentCleared : ChatEvent
    data class ContextManagementPanelExpandedChanged(val isExpanded: Boolean) : ChatEvent
    data class ContextManagementModeSelected(val mode: ContextManagementMode) : ChatEvent
    data class SummaryIntervalChanged(val interval: String) : ChatEvent
    data class SlidingWindowMessagesChanged(val messages: String) : ChatEvent
    data class StickyFactsWindowMessagesChanged(val messages: String) : ChatEvent
    data class CompressionSummaryToggled(val messageIndex: Int) : ChatEvent
    data class ModelFilterChanged(val filter: String) : ChatEvent
    data class ModelSelected(val model: AiModel) : ChatEvent
    data class ModelMenuExpandedChanged(val isExpanded: Boolean) : ChatEvent
    data class TemperatureChanged(val temperature: Float) : ChatEvent
    data class MaxTokensChanged(val maxTokens: String) : ChatEvent
    data class StopWordChanged(val stopWord: String) : ChatEvent
    data class ApiControlChanged(val isEnabled: Boolean) : ChatEvent
    data class RagEnabledChanged(val isEnabled: Boolean) : ChatEvent
    data class RagStrategySelected(val strategy: ChunkingStrategyType) : ChatEvent
    data class RagIndexDirectoryChanged(val indexDirectory: String) : ChatEvent
    data class RagFilteringEnabledChanged(val isEnabled: Boolean) : ChatEvent
    data class RagTopKBeforeFilterChanged(val topK: String) : ChatEvent
    data class RagTopKAfterFilterChanged(val topK: String) : ChatEvent
    data class RagSimilarityThresholdChanged(val threshold: String) : ChatEvent
    data class RagQueryRewriteEnabledChanged(val isEnabled: Boolean) : ChatEvent
    data class RagRerankingEnabledChanged(val isEnabled: Boolean) : ChatEvent
    data class RagRerankerModelDirectoryChanged(val modelDirectory: String) : ChatEvent
    data object SendClicked : ChatEvent
}
