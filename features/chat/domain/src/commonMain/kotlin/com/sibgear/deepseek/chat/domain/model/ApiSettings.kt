package com.sibgear.deepseek.chat.domain.model

data class ApiSettings(
    val temperature: Float = 0.1f,
    val maxTokens: Int = 2500,
    val numCtx: Int = 32768,
    val topP: Float = 0.85f,
    val seed: Int = 42,
    val repeatPenalty: Float = 1.05f,
    val stopWord: String = "",
    val isApiControlEnabled: Boolean = false,
    val isDeepSeekThinkingEnabled: Boolean = false,
)
