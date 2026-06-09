package com.sibgear.deepseek.chat.domain.model

data class ApiSettings(
    val temperature: Float = 0.5f,
    val maxTokens: Int = 2048,
    val stopWord: String = "",
    val isApiControlEnabled: Boolean = false,
)
