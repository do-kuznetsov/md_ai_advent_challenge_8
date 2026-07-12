package com.sibgear.deepseek.chat.history.data.internal.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal const val ChatHistoryFileVersion = 1

@Serializable
internal data class ChatHistoryFileDto(
    val version: Int = ChatHistoryFileVersion,
    val chats: List<ChatHistoryDto> = emptyList(),
)

@Serializable
internal data class LegacyChatHistoryFileDto(
    val version: Int = ChatHistoryFileVersion,
    val messages: List<HistoryMessageDto> = emptyList(),
)

@Serializable
internal data class ChatHistoryDto(
    val chatId: Int,
    val messages: List<HistoryMessageDto> = emptyList(),
    val facts: List<HistoryFactDto> = emptyList(),
    val branches: List<HistoryBranchDto> = emptyList(),
)

@Serializable
internal data class HistoryFactDto(
    val key: String,
    val value: String,
)

@Serializable
internal data class HistoryBranchDto(
    val id: Int,
    val parentId: Int? = null,
    val title: String,
    val summary: String,
)

@Serializable
internal data class HistoryMessageDto(
    val role: String,
    val content: String,
    val thinkingContent: String? = null,
    val branchId: Int? = null,
    val kind: String = HistoryMessageKindDto.Regular.value,
    val apiContent: String? = null,
    val attachment: HistoryMessageAttachmentDto? = null,
    val memory: HistoryMessageMemoryDto? = null,
    val sourceLabel: String? = null,
    val footer: HistoryMessageFooterDto? = null,
)

@Serializable
internal data class HistoryMessageMemoryDto(
    val storedLayers: List<String> = emptyList(),
    val usedLayers: List<String> = emptyList(),
    val changes: List<HistoryMemoryChangeDto> = emptyList(),
    val injectedItems: List<HistoryMemoryItemDto> = emptyList(),
    val error: String? = null,
)

@Serializable
internal data class HistoryMemoryChangeDto(
    val action: String,
    val layer: String,
    val fact: String,
)

@Serializable
internal data class HistoryMemoryItemDto(
    val id: String,
    val layer: String,
    val fact: String,
    val importance: Double,
)

@Serializable
internal data class HistoryMessageAttachmentDto(
    val fileName: String,
    val sizeBytes: Long,
)

@Serializable
internal data class HistoryMessageFooterDto(
    val responseTimeMs: Long,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val cost: Double? = null,
    val retryCount: Int = 0,
)

internal enum class HistoryRoleDto(val value: String) {
    @SerialName("user")
    User("user"),

    @SerialName("assistant")
    Assistant("assistant"),
}

internal enum class HistoryMessageKindDto(val value: String) {
    @SerialName("regular")
    Regular("regular"),

    @SerialName("compression_summary")
    CompressionSummary("compression_summary"),

    @SerialName("task_state_event")
    TaskStateEvent("task_state_event"),

    @SerialName("rag_diagnostic")
    RagDiagnostic("rag_diagnostic"),
}
