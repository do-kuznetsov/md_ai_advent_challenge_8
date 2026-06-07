package com.sibgear.deepseek.domain

enum class AiProvider {
    DeepSeek,
    OpenRouter,
}

data class AiModel(
    val id: String,
    val displayName: String = id,
    val provider: AiProvider = AiProvider.DeepSeek,
    val description: String = "",
    val contextLength: Int? = null,
    val supportedParameters: List<String> = emptyList(),
)
