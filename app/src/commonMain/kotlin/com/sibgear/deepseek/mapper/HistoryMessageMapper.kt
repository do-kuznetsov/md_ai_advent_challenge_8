package com.sibgear.deepseek.mapper

import com.sibgear.deepseek.chat.domain.model.ChatMessage
import com.sibgear.deepseek.chat.domain.model.ChatMessageAttachment
import com.sibgear.deepseek.chat.domain.model.ChatMessageFooter
import com.sibgear.deepseek.chat.domain.model.ChatMessageKind
import com.sibgear.deepseek.chat.domain.model.ChatMessageMemoryMetadata
import com.sibgear.deepseek.chat.domain.model.ChatMemoryChange
import com.sibgear.deepseek.chat.domain.model.ChatMemoryChangeAction
import com.sibgear.deepseek.chat.domain.model.ChatMemoryItem
import com.sibgear.deepseek.chat.domain.model.ChatMemoryLayer
import com.sibgear.deepseek.chat.domain.model.ChatRole
import com.sibgear.deepseek.chat.domain.model.ChatBranch
import com.sibgear.deepseek.chat.domain.model.StickyFact
import com.sibgear.deepseek.chat.history.domain.model.HistoryBranch
import com.sibgear.deepseek.chat.history.domain.model.HistoryFact
import com.sibgear.deepseek.chat.history.domain.model.HistoryMemoryChange
import com.sibgear.deepseek.chat.history.domain.model.HistoryMemoryChangeAction
import com.sibgear.deepseek.chat.history.domain.model.HistoryMemoryItem
import com.sibgear.deepseek.chat.history.domain.model.HistoryMemoryLayer
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessage
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageAttachment
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageFooter
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageKind
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageMemoryMetadata
import com.sibgear.deepseek.chat.history.domain.model.HistoryRole

internal fun List<HistoryMessage>.toChatMessages(): List<ChatMessage> =
    map { it.toChatMessage() }

internal fun List<ChatMessage>.toHistoryMessages(): List<HistoryMessage> =
    map { it.toHistoryMessage() }

internal fun List<HistoryFact>.toStickyFacts(): List<StickyFact> =
    map { fact ->
        StickyFact(
            key = fact.key,
            value = fact.value,
        )
    }

internal fun List<StickyFact>.toHistoryFacts(): List<HistoryFact> =
    map { fact ->
        HistoryFact(
            key = fact.key,
            value = fact.value,
        )
    }

internal fun List<HistoryBranch>.toChatBranches(): List<ChatBranch> =
    map { branch ->
        ChatBranch(
            id = branch.id,
            parentId = branch.parentId,
            title = branch.title,
            summary = branch.summary,
        )
    }

internal fun List<ChatBranch>.toHistoryBranches(): List<HistoryBranch> =
    map { branch ->
        HistoryBranch(
            id = branch.id,
            parentId = branch.parentId,
            title = branch.title,
            summary = branch.summary,
        )
    }

private fun HistoryMessage.toChatMessage(): ChatMessage =
    ChatMessage(
        role = role.toChatRole(),
        content = content,
        branchId = branchId,
        kind = kind.toChatMessageKind(),
        apiContent = apiContent,
        attachment = attachment?.toChatMessageAttachment(),
        memory = memory?.toChatMemoryMetadata(),
        sourceLabel = sourceLabel,
        footer = footer?.toChatMessageFooter(),
    )

private fun ChatMessage.toHistoryMessage(): HistoryMessage =
    HistoryMessage(
        role = role.toHistoryRole(),
        content = content,
        branchId = branchId,
        kind = kind.toHistoryMessageKind(),
        apiContent = apiContent,
        attachment = attachment?.toHistoryMessageAttachment(),
        memory = memory?.toHistoryMemoryMetadata(),
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
        HistoryMessageKind.TaskStateEvent -> ChatMessageKind.TaskStateEvent
    }

private fun ChatMessageKind.toHistoryMessageKind(): HistoryMessageKind =
    when (this) {
        ChatMessageKind.Regular -> HistoryMessageKind.Regular
        ChatMessageKind.CompressionSummary -> HistoryMessageKind.CompressionSummary
        ChatMessageKind.TaskStateEvent -> HistoryMessageKind.TaskStateEvent
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

private fun HistoryMessageMemoryMetadata.toChatMemoryMetadata(): ChatMessageMemoryMetadata =
    ChatMessageMemoryMetadata(
        storedLayers = storedLayers.map { it.toChatMemoryLayer() },
        usedLayers = usedLayers.map { it.toChatMemoryLayer() },
        changes = changes.map {
            ChatMemoryChange(
                action = it.action.toChatMemoryChangeAction(),
                layer = it.layer.toChatMemoryLayer(),
                fact = it.fact,
            )
        },
        injectedItems = injectedItems.map {
            ChatMemoryItem(
                id = it.id,
                layer = it.layer.toChatMemoryLayer(),
                fact = it.fact,
                importance = it.importance,
            )
        },
        error = error,
    )

private fun ChatMessageMemoryMetadata.toHistoryMemoryMetadata(): HistoryMessageMemoryMetadata =
    HistoryMessageMemoryMetadata(
        storedLayers = storedLayers.map { it.toHistoryMemoryLayer() },
        usedLayers = usedLayers.map { it.toHistoryMemoryLayer() },
        changes = changes.map {
            HistoryMemoryChange(
                action = it.action.toHistoryMemoryChangeAction(),
                layer = it.layer.toHistoryMemoryLayer(),
                fact = it.fact,
            )
        },
        injectedItems = injectedItems.map {
            HistoryMemoryItem(
                id = it.id,
                layer = it.layer.toHistoryMemoryLayer(),
                fact = it.fact,
                importance = it.importance,
            )
        },
        error = error,
    )

private fun HistoryMemoryLayer.toChatMemoryLayer(): ChatMemoryLayer =
    when (this) {
        HistoryMemoryLayer.ShortTerm -> ChatMemoryLayer.ShortTerm
        HistoryMemoryLayer.WorkingMemory -> ChatMemoryLayer.WorkingMemory
        HistoryMemoryLayer.LongTermMemory -> ChatMemoryLayer.LongTermMemory
    }

private fun ChatMemoryLayer.toHistoryMemoryLayer(): HistoryMemoryLayer =
    when (this) {
        ChatMemoryLayer.ShortTerm -> HistoryMemoryLayer.ShortTerm
        ChatMemoryLayer.WorkingMemory -> HistoryMemoryLayer.WorkingMemory
        ChatMemoryLayer.LongTermMemory -> HistoryMemoryLayer.LongTermMemory
    }

private fun HistoryMemoryChangeAction.toChatMemoryChangeAction(): ChatMemoryChangeAction =
    when (this) {
        HistoryMemoryChangeAction.Add -> ChatMemoryChangeAction.Add
        HistoryMemoryChangeAction.Update -> ChatMemoryChangeAction.Update
        HistoryMemoryChangeAction.Delete -> ChatMemoryChangeAction.Delete
    }

private fun ChatMemoryChangeAction.toHistoryMemoryChangeAction(): HistoryMemoryChangeAction =
    when (this) {
        ChatMemoryChangeAction.Add -> HistoryMemoryChangeAction.Add
        ChatMemoryChangeAction.Update -> HistoryMemoryChangeAction.Update
        ChatMemoryChangeAction.Delete -> HistoryMemoryChangeAction.Delete
    }
