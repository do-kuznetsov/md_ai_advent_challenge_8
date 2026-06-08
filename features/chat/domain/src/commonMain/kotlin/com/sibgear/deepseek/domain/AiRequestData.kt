package com.sibgear.deepseek.domain

data class AiRequestData(
    val systemPrompt: String,
    val prompt: String,
    val model: AiModel,
    val apiSettings: ApiSettings,
)
