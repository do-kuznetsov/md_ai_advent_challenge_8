package com.sibgear.deepseek.chat.history.domain.model

data class HistoryMessage(
    val role: HistoryRole,
    val content: String,
    val branchId: Int? = null,
    val kind: HistoryMessageKind = HistoryMessageKind.Regular,
    val apiContent: String? = null,
    val attachment: HistoryMessageAttachment? = null,
    val memory: HistoryMessageMemoryMetadata? = null,
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
    TaskStateEvent,
    RagDiagnostic,
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

data class HistoryFact(
    val key: String,
    val value: String,
)

data class HistoryBranch(
    val id: Int,
    val parentId: Int? = null,
    val title: String,
    val summary: String,
)

data class HistoryMessageMemoryMetadata(
    val storedLayers: List<HistoryMemoryLayer> = emptyList(),
    val usedLayers: List<HistoryMemoryLayer> = emptyList(),
    val changes: List<HistoryMemoryChange> = emptyList(),
    val injectedItems: List<HistoryMemoryItem> = emptyList(),
    val error: String? = null,
)

data class HistoryMemoryChange(
    val action: HistoryMemoryChangeAction,
    val layer: HistoryMemoryLayer,
    val fact: String,
)

data class HistoryMemoryItem(
    val id: String,
    val layer: HistoryMemoryLayer,
    val fact: String,
    val importance: Double,
)

enum class HistoryMemoryLayer {
    ShortTerm,
    WorkingMemory,
    LongTermMemory,
}

enum class HistoryMemoryChangeAction {
    Add,
    Update,
    Delete,
}
