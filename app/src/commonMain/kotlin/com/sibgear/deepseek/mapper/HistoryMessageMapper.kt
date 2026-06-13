package com.sibgear.deepseek.mapper

import com.sibgear.deepseek.chat.domain.model.ChatMessage
import com.sibgear.deepseek.chat.domain.model.ChatMessageAttachment
import com.sibgear.deepseek.chat.domain.model.ChatMessageFooter
import com.sibgear.deepseek.chat.domain.model.ChatMessageKind
import com.sibgear.deepseek.chat.domain.model.ChatRole
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessage
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageAttachment
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageFooter
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageKind
import com.sibgear.deepseek.chat.history.domain.model.HistoryRole

internal fun List<HistoryMessage>.toChatMessages(): List<ChatMessage> =
    map { it.toChatMessage() }

internal fun List<ChatMessage>.toHistoryMessages(): List<HistoryMessage> =
    map { it.toHistoryMessage() }

private fun HistoryMessage.toChatMessage(): ChatMessage =
    ChatMessage(
        role = role.toChatRole(),
        content = content,
        kind = kind.toChatMessageKind(),
        apiContent = apiContent,
        attachment = attachment?.toChatMessageAttachment(),
        sourceLabel = sourceLabel,
        footer = footer?.toChatMessageFooter(),
    )

private fun ChatMessage.toHistoryMessage(): HistoryMessage =
    HistoryMessage(
        role = role.toHistoryRole(),
        content = content,
        kind = kind.toHistoryMessageKind(),
        apiContent = apiContent,
        attachment = attachment?.toHistoryMessageAttachment(),
        sourceLabel = sourceLabel,
        footer = footer?.toHistoryMessageFooter(),
    )

private fun HistoryRole.toChatRole(): ChatRole =
    when (this) {
        HistoryRole.User -> ChatRole.User
        HistoryRole.Assistant -> ChatRole.Assistant
    }

private fun ChatRole.toHistoryRole(): HistoryRole =
    when (this) {
        ChatRole.User -> HistoryRole.User
        ChatRole.Assistant -> HistoryRole.Assistant
    }

private fun HistoryMessageKind.toChatMessageKind(): ChatMessageKind =
    when (this) {
        HistoryMessageKind.Regular -> ChatMessageKind.Regular
        HistoryMessageKind.CompressionSummary -> ChatMessageKind.CompressionSummary
    }

private fun ChatMessageKind.toHistoryMessageKind(): HistoryMessageKind =
    when (this) {
        ChatMessageKind.Regular -> HistoryMessageKind.Regular
        ChatMessageKind.CompressionSummary -> HistoryMessageKind.CompressionSummary
    }

private fun HistoryMessageFooter.toChatMessageFooter(): ChatMessageFooter =
    ChatMessageFooter(
        responseTimeMs = responseTimeMs,
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        totalTokens = totalTokens,
        cost = cost,
        retryCount = retryCount,
    )

private fun ChatMessageFooter.toHistoryMessageFooter(): HistoryMessageFooter =
    HistoryMessageFooter(
        responseTimeMs = responseTimeMs,
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        totalTokens = totalTokens,
        cost = cost,
        retryCount = retryCount,
    )

private fun HistoryMessageAttachment.toChatMessageAttachment(): ChatMessageAttachment =
    ChatMessageAttachment(
        fileName = fileName,
        sizeBytes = sizeBytes,
    )

private fun ChatMessageAttachment.toHistoryMessageAttachment(): HistoryMessageAttachment =
    HistoryMessageAttachment(
        fileName = fileName,
        sizeBytes = sizeBytes,
    )
