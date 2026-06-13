package com.sibgear.deepseek.chat.domain.model

data class ContextMessage(
    val role: ChatRole,
    val kind: ChatMessageKind = ChatMessageKind.Regular,
    val content: String,
)

data class ContextPlan(
    val apiMessages: List<ContextMessage>,
    val compressionRequest: CompressionRequest? = null,
)

data class CompressionRequest(
    val messages: List<ContextMessage>,
    val prompt: String,
)

const val CompressionSummaryPrompt = "сделай краткое резюме по диалогу"
