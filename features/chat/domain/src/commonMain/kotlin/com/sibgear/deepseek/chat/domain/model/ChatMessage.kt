package com.sibgear.deepseek.chat.domain.model

data class ChatMessage(
    val role: ChatRole,
    val content: String,
    val kind: ChatMessageKind = ChatMessageKind.Regular,
    val apiContent: String? = null,
    val attachment: ChatMessageAttachment? = null,
    val sourceLabel: String? = null,
    val footer: ChatMessageFooter? = null,
)

enum class ChatRole {
    User,
    Assistant,
}

enum class ChatMessageKind {
    Regular,
    CompressionSummary,
}

data class ChatMessageFooter(
    val responseTimeMs: Long,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val cost: Double? = null,
    val retryCount: Int = 0,
)

data class ChatMessageAttachment(
    val fileName: String,
    val sizeBytes: Long,
)
