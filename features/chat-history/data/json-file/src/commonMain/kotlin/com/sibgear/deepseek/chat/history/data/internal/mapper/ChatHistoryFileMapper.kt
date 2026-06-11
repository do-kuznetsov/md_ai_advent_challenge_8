package com.sibgear.deepseek.chat.history.data.internal.mapper

import com.sibgear.deepseek.chat.history.data.internal.model.ChatHistoryFileDto
import com.sibgear.deepseek.chat.history.data.internal.model.ChatHistoryFileVersion
import com.sibgear.deepseek.chat.history.data.internal.model.ChatHistoryDto
import com.sibgear.deepseek.chat.history.data.internal.model.HistoryMessageAttachmentDto
import com.sibgear.deepseek.chat.history.data.internal.model.HistoryMessageDto
import com.sibgear.deepseek.chat.history.data.internal.model.HistoryMessageFooterDto
import com.sibgear.deepseek.chat.history.data.internal.model.HistoryRoleDto
import com.sibgear.deepseek.chat.history.data.internal.model.LegacyChatHistoryFileDto
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessage
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageAttachment
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageFooter
import com.sibgear.deepseek.chat.history.domain.model.HistoryRole

internal fun List<HistoryMessage>.toChatHistoryFileDto(): ChatHistoryFileDto =
    ChatHistoryFileDto(
        version = ChatHistoryFileVersion,
        chats = listOf(
            ChatHistoryDto(
                chatId = LegacySingleChatId,
                messages = map { it.toDto() },
            ),
        ),
    )

internal fun ChatHistoryFileDto.toHistoryMessages(): List<HistoryMessage> =
    chats.firstOrNull { it.chatId == LegacySingleChatId }
        ?.messages
        .orEmpty()
        .mapNotNull { it.toDomain() }

internal fun Map<Int, List<HistoryMessage>>.toChatHistoriesFileDto(): ChatHistoryFileDto =
    ChatHistoryFileDto(
        version = ChatHistoryFileVersion,
        chats = entries
            .sortedBy { it.key }
            .map { (chatId, messages) ->
                ChatHistoryDto(
                    chatId = chatId,
                    messages = messages.map { it.toDto() },
                )
            },
    )

internal fun ChatHistoryFileDto.toHistoryMessagesByChatId(): Map<Int, List<HistoryMessage>> =
    chats.associate { chat ->
        chat.chatId to chat.messages.mapNotNull { it.toDomain() }
    }

internal fun LegacyChatHistoryFileDto.toHistoryMessages(): List<HistoryMessage> =
    messages.mapNotNull { it.toDomain() }

private fun HistoryMessage.toDto(): HistoryMessageDto =
    HistoryMessageDto(
        role = role.toDto().value,
        content = content,
        apiContent = apiContent,
        attachment = attachment?.toDto(),
        sourceLabel = sourceLabel,
        footer = footer?.toDto(),
    )

private fun HistoryMessageDto.toDomain(): HistoryMessage? {
    val domainRole = role.toHistoryRole() ?: return null
    return HistoryMessage(
        role = domainRole,
        content = content,
        apiContent = apiContent,
        attachment = attachment?.toDomain(),
        sourceLabel = sourceLabel,
        footer = footer?.toDomain(),
    )
}

private fun HistoryRole.toDto(): HistoryRoleDto =
    when (this) {
        HistoryRole.User -> HistoryRoleDto.User
        HistoryRole.Assistant -> HistoryRoleDto.Assistant
    }

private fun String.toHistoryRole(): HistoryRole? =
    when (this) {
        HistoryRoleDto.User.value -> HistoryRole.User
        HistoryRoleDto.Assistant.value -> HistoryRole.Assistant
        else -> null
    }

private fun HistoryMessageFooter.toDto(): HistoryMessageFooterDto =
    HistoryMessageFooterDto(
        responseTimeMs = responseTimeMs,
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        totalTokens = totalTokens,
        cost = cost,
        retryCount = retryCount,
    )

private fun HistoryMessageAttachment.toDto(): HistoryMessageAttachmentDto =
    HistoryMessageAttachmentDto(
        fileName = fileName,
        sizeBytes = sizeBytes,
    )

private fun HistoryMessageAttachmentDto.toDomain(): HistoryMessageAttachment =
    HistoryMessageAttachment(
        fileName = fileName,
        sizeBytes = sizeBytes,
    )

private fun HistoryMessageFooterDto.toDomain(): HistoryMessageFooter =
    HistoryMessageFooter(
        responseTimeMs = responseTimeMs,
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        totalTokens = totalTokens,
        cost = cost,
        retryCount = retryCount,
    )

private const val LegacySingleChatId = 1
