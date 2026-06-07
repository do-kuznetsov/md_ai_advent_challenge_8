package com.sibgear.deepseek.domain

data class AiRequestData(
    val deepSeekApiKey: String,
    val openRouterApiKey: String,
    val systemPrompt: String,
    val prompt: String,
    val model: AiModel,
    val apiSettings: ApiSettings,
)
