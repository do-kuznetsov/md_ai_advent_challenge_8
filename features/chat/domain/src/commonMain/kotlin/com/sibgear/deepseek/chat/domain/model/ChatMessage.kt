package com.sibgear.deepseek.chat.domain.model

data class ChatMessage(
    val role: ChatRole,
    val content: String,
    val branchId: Int? = null,
    val kind: ChatMessageKind = ChatMessageKind.Regular,
    val apiContent: String? = null,
    val attachment: ChatMessageAttachment? = null,
    val memory: ChatMessageMemoryMetadata? = null,
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
    TaskStateEvent,
    RagDiagnostic,
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

data class ChatMessageMemoryMetadata(
    val storedLayers: List<ChatMemoryLayer> = emptyList(),
    val usedLayers: List<ChatMemoryLayer> = emptyList(),
    val changes: List<ChatMemoryChange> = emptyList(),
    val injectedItems: List<ChatMemoryItem> = emptyList(),
    val error: String? = null,
)

data class ChatMemoryChange(
    val action: ChatMemoryChangeAction,
    val layer: ChatMemoryLayer,
    val fact: String,
)

enum class ChatMemoryChangeAction {
    Add,
    Update,
    Delete,
}
