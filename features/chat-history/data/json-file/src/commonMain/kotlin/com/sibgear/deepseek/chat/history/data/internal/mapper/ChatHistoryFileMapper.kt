package com.sibgear.deepseek.chat.history.data.internal.mapper

import com.sibgear.deepseek.chat.history.data.internal.model.ChatHistoryFileDto
import com.sibgear.deepseek.chat.history.data.internal.model.ChatHistoryFileVersion
import com.sibgear.deepseek.chat.history.data.internal.model.ChatHistoryDto
import com.sibgear.deepseek.chat.history.data.internal.model.HistoryBranchDto
import com.sibgear.deepseek.chat.history.data.internal.model.HistoryFactDto
import com.sibgear.deepseek.chat.history.data.internal.model.HistoryMemoryChangeDto
import com.sibgear.deepseek.chat.history.data.internal.model.HistoryMemoryItemDto
import com.sibgear.deepseek.chat.history.data.internal.model.HistoryMessageAttachmentDto
import com.sibgear.deepseek.chat.history.data.internal.model.HistoryMessageDto
import com.sibgear.deepseek.chat.history.data.internal.model.HistoryMessageFooterDto
import com.sibgear.deepseek.chat.history.data.internal.model.HistoryMessageKindDto
import com.sibgear.deepseek.chat.history.data.internal.model.HistoryMessageMemoryDto
import com.sibgear.deepseek.chat.history.data.internal.model.HistoryRoleDto
import com.sibgear.deepseek.chat.history.data.internal.model.LegacyChatHistoryFileDto
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

internal fun ChatHistoryFileDto.toChatDataByChatId(): Map<Int, ChatHistoryData> =
    chats.associate { chat ->
        chat.chatId to ChatHistoryData(
            messages = chat.messages.mapNotNull { it.toDomain() },
            facts = chat.facts.mapNotNull { it.toDomain() },
            branches = chat.branches.mapNotNull { it.toDomain() },
        )
    }

internal fun Map<Int, ChatHistoryData>.toChatHistoriesDataFileDto(): ChatHistoryFileDto =
    ChatHistoryFileDto(
        version = ChatHistoryFileVersion,
        chats = entries
            .sortedBy { it.key }
            .map { (chatId, data) ->
                ChatHistoryDto(
                    chatId = chatId,
                    messages = data.messages.map { it.toDto() },
                    facts = data.facts.map { it.toDto() },
                    branches = data.branches.map { it.toDto() },
                )
            },
    )

internal fun LegacyChatHistoryFileDto.toHistoryMessages(): List<HistoryMessage> =
    messages.mapNotNull { it.toDomain() }

private fun HistoryMessage.toDto(): HistoryMessageDto =
    HistoryMessageDto(
        role = role.toDto().value,
        content = content,
        branchId = branchId,
        kind = kind.toDto().value,
        apiContent = apiContent,
        attachment = attachment?.toDto(),
        memory = memory?.toDto(),
        sourceLabel = sourceLabel,
        footer = footer?.toDto(),
    )

private fun HistoryBranch.toDto(): HistoryBranchDto =
    HistoryBranchDto(
        id = id,
        parentId = parentId,
        title = title,
        summary = summary,
    )

private fun HistoryBranchDto.toDomain(): HistoryBranch? {
    if (id <= 0) {
        return null
    }

    val trimmedTitle = title.trim()
    val trimmedSummary = summary.trim()
    return HistoryBranch(
        id = id,
        parentId = parentId?.takeIf { it > 0 },
        title = trimmedTitle.takeIf { it.isNotEmpty() } ?: "Branch $id",
        summary = trimmedSummary.takeIf { it.isNotEmpty() } ?: trimmedTitle.ifBlank { "Branch $id" },
    )
}

private fun HistoryFact.toDto(): HistoryFactDto =
    HistoryFactDto(
        key = key,
        value = value,
    )

private fun HistoryFactDto.toDomain(): HistoryFact? {
    val trimmedKey = key.trim()
    val trimmedValue = value.trim()
    if (trimmedKey.isEmpty() || trimmedValue.isEmpty()) {
        return null
    }

    return HistoryFact(
        key = trimmedKey,
        value = trimmedValue,
    )
}

private fun HistoryMessageDto.toDomain(): HistoryMessage? {
    val domainRole = role.toHistoryRole() ?: return null
    return HistoryMessage(
        role = domainRole,
        content = content,
        branchId = branchId,
        kind = kind.toHistoryMessageKind(),
        apiContent = apiContent,
        attachment = attachment?.toDomain(),
        memory = memory?.toDomain(),
        sourceLabel = sourceLabel,
        footer = footer?.toDomain(),
    )
}

private fun HistoryRole.toDto(): HistoryRoleDto =
    when (this) {
        HistoryRole.User -> HistoryRoleDto.User
        HistoryRole.Assistant -> HistoryRoleDto.Assistant
    }

private fun HistoryMessageKind.toDto(): HistoryMessageKindDto =
    when (this) {
        HistoryMessageKind.Regular -> HistoryMessageKindDto.Regular
        HistoryMessageKind.CompressionSummary -> HistoryMessageKindDto.CompressionSummary
        HistoryMessageKind.TaskStateEvent -> HistoryMessageKindDto.TaskStateEvent
        HistoryMessageKind.RagDiagnostic -> HistoryMessageKindDto.RagDiagnostic
    }

private fun String.toHistoryMessageKind(): HistoryMessageKind =
    when (this) {
        HistoryMessageKindDto.CompressionSummary.value -> HistoryMessageKind.CompressionSummary
        HistoryMessageKindDto.TaskStateEvent.value -> HistoryMessageKind.TaskStateEvent
        HistoryMessageKindDto.RagDiagnostic.value -> HistoryMessageKind.RagDiagnostic
        else -> HistoryMessageKind.Regular
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

private fun HistoryMessageMemoryMetadata.toDto(): HistoryMessageMemoryDto =
    HistoryMessageMemoryDto(
        storedLayers = storedLayers.map { it.toDtoValue() },
        usedLayers = usedLayers.map { it.toDtoValue() },
        changes = changes.map { it.toDto() },
        injectedItems = injectedItems.map { it.toDto() },
        error = error,
    )

private fun HistoryMessageMemoryDto.toDomain(): HistoryMessageMemoryMetadata =
    HistoryMessageMemoryMetadata(
        storedLayers = storedLayers.mapNotNull { it.toHistoryMemoryLayer() },
        usedLayers = usedLayers.mapNotNull { it.toHistoryMemoryLayer() },
        changes = changes.mapNotNull { it.toDomain() },
        injectedItems = injectedItems.mapNotNull { it.toDomain() },
        error = error,
    )

private fun HistoryMemoryChange.toDto(): HistoryMemoryChangeDto =
    HistoryMemoryChangeDto(
        action = action.toDtoValue(),
        layer = layer.toDtoValue(),
        fact = fact,
    )

private fun HistoryMemoryChangeDto.toDomain(): HistoryMemoryChange? {
    val trimmedFact = fact.trim()
    if (trimmedFact.isEmpty()) {
        return null
    }

    return HistoryMemoryChange(
        action = action.toHistoryMemoryChangeAction() ?: return null,
        layer = layer.toHistoryMemoryLayer() ?: return null,
        fact = trimmedFact,
    )
}

private fun HistoryMemoryItem.toDto(): HistoryMemoryItemDto =
    HistoryMemoryItemDto(
        id = id,
        layer = layer.toDtoValue(),
        fact = fact,
        importance = importance,
    )

private fun HistoryMemoryItemDto.toDomain(): HistoryMemoryItem? {
    val trimmedId = id.trim()
    val trimmedFact = fact.trim()
    if (trimmedId.isEmpty() || trimmedFact.isEmpty()) {
        return null
    }

    return HistoryMemoryItem(
        id = trimmedId,
        layer = layer.toHistoryMemoryLayer() ?: return null,
        fact = trimmedFact,
        importance = importance.coerceIn(0.0, 1.0),
    )
}

private fun HistoryMemoryLayer.toDtoValue(): String =
    when (this) {
        HistoryMemoryLayer.ShortTerm -> "short_term"
        HistoryMemoryLayer.WorkingMemory -> "working_memory"
        HistoryMemoryLayer.LongTermMemory -> "long_term_memory"
    }

private fun String.toHistoryMemoryLayer(): HistoryMemoryLayer? =
    when (this) {
        "short_term" -> HistoryMemoryLayer.ShortTerm
        "working_memory" -> HistoryMemoryLayer.WorkingMemory
        "long_term_memory" -> HistoryMemoryLayer.LongTermMemory
        else -> null
    }

private fun HistoryMemoryChangeAction.toDtoValue(): String =
    when (this) {
        HistoryMemoryChangeAction.Add -> "add"
        HistoryMemoryChangeAction.Update -> "update"
        HistoryMemoryChangeAction.Delete -> "delete"
    }

private fun String.toHistoryMemoryChangeAction(): HistoryMemoryChangeAction? =
    when (this) {
        "add" -> HistoryMemoryChangeAction.Add
        "update" -> HistoryMemoryChangeAction.Update
        "delete" -> HistoryMemoryChangeAction.Delete
        else -> null
    }

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

internal data class ChatHistoryData(
    val messages: List<HistoryMessage> = emptyList(),
    val facts: List<HistoryFact> = emptyList(),
    val branches: List<HistoryBranch> = emptyList(),
)
