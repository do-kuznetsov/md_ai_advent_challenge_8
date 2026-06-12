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
)

@Serializable
internal data class HistoryMessageDto(
    val role: String,
    val content: String,
    val apiContent: String? = null,
    val attachment: HistoryMessageAttachmentDto? = null,
    val sourceLabel: String? = null,
    val footer: HistoryMessageFooterDto? = null,
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
