package com.sibgear.deepseek.ui

import com.sibgear.deepseek.domain.ApiSettings
import com.sibgear.deepseek.domain.DeepSeekModel
import com.sibgear.deepseek.domain.DeepSeekModels

data class DeepSeekViewState(
    val prompt: String = "",
    val apiKey: String = "",
    val selectedModel: DeepSeekModel = DeepSeekModels.Default,
    val availableModels: List<DeepSeekModel> = DeepSeekModels.Available,
    val output: String = "Ответ DeepSeek появится здесь.",
    val isLoading: Boolean = false,
    val isModelMenuExpanded: Boolean = false,
    val apiSettings: ApiSettings = ApiSettings(),
    val maxTokensInput: String = ApiSettings().maxTokens.toString(),
) {
    val isSendEnabled: Boolean
        get() = apiKey.isNotBlank() && !isLoading
}
