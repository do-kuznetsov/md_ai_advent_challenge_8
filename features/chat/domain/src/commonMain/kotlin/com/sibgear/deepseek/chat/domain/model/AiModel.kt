package com.sibgear.deepseek.chat.domain.model

enum class AiProvider {
    DeepSeek,
    OpenRouter,
    MagnitCopilot,
    Ollama,
}

data class AiModel(
    val id: String,
    val displayName: String = id,
    val provider: AiProvider = AiProvider.DeepSeek,
    val description: String = "",
    val contextLength: Int? = null,
    val supportedParameters: List<String> = emptyList(),
)
