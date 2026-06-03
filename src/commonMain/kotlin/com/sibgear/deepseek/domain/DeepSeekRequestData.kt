package com.sibgear.deepseek.domain

data class DeepSeekRequestData(
    val apiKey: String,
    val systemPrompt: String,
    val prompt: String,
    val model: DeepSeekModel,
    val apiSettings: ApiSettings,
)
