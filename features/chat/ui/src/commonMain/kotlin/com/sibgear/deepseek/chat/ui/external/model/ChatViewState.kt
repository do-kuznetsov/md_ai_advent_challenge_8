package com.sibgear.deepseek.chat.ui.external.model

import com.sibgear.deepseek.chat.domain.model.ApiSettings
import com.sibgear.deepseek.chat.domain.model.ChatMessage
import com.sibgear.deepseek.chat.domain.model.AiModel
import com.sibgear.deepseek.chat.domain.model.ContextManagementMode
import com.sibgear.deepseek.chat.domain.model.DefaultContextManagementMessages
import com.sibgear.deepseek.chat.domain.model.PromptAttachment
import com.sibgear.deepseek.chat.ui.internal.mapper.buildContextUsageLabel
import com.sibgear.deepseek.chat.ui.internal.mapper.buildPinnedContextMessageIndex
import com.sibgear.deepseek.chat.ui.internal.model.ChatDefaults

data class ChatViewState(
    val systemPrompt: String = "",
    val prompt: String = "",
    val attachment: PromptAttachment? = null,
    val attachmentError: String? = null,
    val selectedModel: AiModel = ChatDefaults.DefaultModel,
    val openRouterModels: List<AiModel> = emptyList(),
    val deepSeekModels: List<AiModel> = listOf(ChatDefaults.DefaultModel),
    val modelFilter: String = "free",
    val openRouterModelsStatus: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val contextUsageLabel: String = buildContextUsageLabel(messages, selectedModel),
    val isLoading: Boolean = false,
    val isModelMenuExpanded: Boolean = false,
    val contextManagementMode: ContextManagementMode = ContextManagementMode.None,
    val isContextManagementPanelExpanded: Boolean = false,
    val summaryIntervalInput: String = DefaultContextManagementMessages.toString(),
    val slidingWindowMessagesInput: String = DefaultContextManagementMessages.toString(),
    val pinnedContextMessageIndex: Int? = buildPinnedContextMessageIndex(
        messages = messages,
        mode = contextManagementMode,
        slidingWindowMessagesInput = slidingWindowMessagesInput,
    ),
    val expandedCompressionMessageIndexes: Set<Int> = emptySet(),
    val apiSettings: ApiSettings = ApiSettings(),
    val maxTokensInput: String = ApiSettings().maxTokens.toString(),
) {
    val availableModels: List<AiModel>
        get() = openRouterModels + deepSeekModels

    val isSendEnabled: Boolean
        get() = prompt.isNotBlank() && !isLoading
}
