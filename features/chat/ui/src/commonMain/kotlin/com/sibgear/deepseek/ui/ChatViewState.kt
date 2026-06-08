package com.sibgear.deepseek.ui

import com.sibgear.deepseek.domain.ApiSettings
import com.sibgear.deepseek.domain.ChatMessage
import com.sibgear.deepseek.domain.AiModel
import com.sibgear.deepseek.domain.DeepSeekModels

data class ChatViewState(
    val systemPrompt: String = "",
    val prompt: String = "",
    val selectedModel: AiModel = DeepSeekModels.Default,
    val openRouterModels: List<AiModel> = emptyList(),
    val modelFilter: String = "free",
    val openRouterModelsStatus: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isModelMenuExpanded: Boolean = false,
    val apiSettings: ApiSettings = ApiSettings(),
    val maxTokensInput: String = ApiSettings().maxTokens.toString(),
) {
    val deepSeekModels: List<AiModel>
        get() = DeepSeekModels.Available

    val availableModels: List<AiModel>
        get() = openRouterModels + deepSeekModels

    val isSendEnabled: Boolean
        get() = prompt.isNotBlank() && !isLoading
}
