package com.sibgear.deepseek.domain

data class ApiSettings(
    val temperature: Float = 0.5f,
    val maxTokens: Int = 2048,
    val stopWord: String = "",
    val isApiControlEnabled: Boolean = false,
)
