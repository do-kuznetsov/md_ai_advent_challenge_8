package com.sibgear.deepseek.chat.domain.model

data class AiRequestData(
    val systemPrompt: String,
    val prompt: String,
    val model: AiModel,
    val apiSettings: ApiSettings,
)
