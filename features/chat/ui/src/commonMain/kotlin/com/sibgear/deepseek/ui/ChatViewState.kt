package com.sibgear.deepseek.ui

import com.sibgear.deepseek.domain.ApiSettings
import com.sibgear.deepseek.domain.ChatMessage
import com.sibgear.deepseek.domain.AiModel

data class ChatViewState(
    val systemPrompt: String = "",
    val prompt: String = "",
    val selectedModel: AiModel = ChatDefaults.DefaultModel,
    val openRouterModels: List<AiModel> = emptyList(),
    val deepSeekModels: List<AiModel> = listOf(ChatDefaults.DefaultModel),
    val modelFilter: String = "free",
    val openRouterModelsStatus: String? = null,
    val messages: List<ChatMessage> = emptyList(),
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
