package com.sibgear.deepseek.domain

data class ChatMessage(
    val role: ChatRole,
    val content: String,
    val sourceLabel: String? = null,
    val footer: ChatMessageFooter? = null,
)

enum class ChatRole {
    User,
    Assistant,
}

data class ChatMessageFooter(
    val responseTimeMs: Long,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val cost: Double? = null,
    val retryCount: Int = 0,
)
