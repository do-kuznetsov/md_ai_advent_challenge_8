package com.sibgear.deepseek.chat.history.domain.model

data class HistoryMessage(
    val role: HistoryRole,
    val content: String,
    val kind: HistoryMessageKind = HistoryMessageKind.Regular,
    val apiContent: String? = null,
    val attachment: HistoryMessageAttachment? = null,
    val sourceLabel: String? = null,
    val footer: HistoryMessageFooter? = null,
)

enum class HistoryRole {
    User,
    Assistant,
}

enum class HistoryMessageKind {
    Regular,
    CompressionSummary,
}

data class HistoryMessageFooter(
    val responseTimeMs: Long,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val cost: Double? = null,
    val retryCount: Int = 0,
)

data class HistoryMessageAttachment(
    val fileName: String,
    val sizeBytes: Long,
)
