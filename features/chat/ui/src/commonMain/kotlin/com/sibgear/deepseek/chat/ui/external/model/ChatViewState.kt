package com.sibgear.deepseek.chat.ui.external.model

import com.sibgear.deepseek.chat.domain.model.ApiSettings
import com.sibgear.deepseek.chat.domain.model.ChatMessage
import com.sibgear.deepseek.chat.domain.model.AiModel
import com.sibgear.deepseek.chat.ui.internal.mapper.buildContextUsageLabel
import com.sibgear.deepseek.chat.ui.internal.model.ChatDefaults

data class ChatViewState(
    val systemPrompt: String = "",
    val prompt: String = "",
    val selectedModel: AiModel = ChatDefaults.DefaultModel,
    val openRouterModels: List<AiModel> = emptyList(),
    val deepSeekModels: List<AiModel> = listOf(ChatDefaults.DefaultModel),
    val modelFilter: String = "free",
    val openRouterModelsStatus: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val contextUsageLabel: String = buildContextUsageLabel(messages, selectedModel),
    val isLoading: Boolean = false,
    val isModelMenuExpanded: Boolean = false,
    val apiSettings: ApiSettings = ApiSettings(),
    val maxTokensInput: String = ApiSettings().maxTokens.toString(),
) {
    val availableModels: List<AiModel>
        get() = openRouterModels + deepSeekModels

    val isSendEnabled: Boolean
        get() = prompt.isNotBlank() && !isLoading
}
