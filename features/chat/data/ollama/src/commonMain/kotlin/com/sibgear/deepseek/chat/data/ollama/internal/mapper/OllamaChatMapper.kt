package com.sibgear.deepseek.chat.data.ollama.internal.mapper

import com.sibgear.deepseek.assistant.memory.domain.model.AssistantInvariant
import com.sibgear.deepseek.assistant.memory.domain.model.InvariantCategory
import com.sibgear.deepseek.assistant.memory.domain.model.MemoryItem
import com.sibgear.deepseek.assistant.memory.domain.model.MemoryLayer
import com.sibgear.deepseek.chat.data.ollama.internal.model.OllamaChatMessage
import com.sibgear.deepseek.chat.data.ollama.internal.model.OllamaChatOptions
import com.sibgear.deepseek.chat.data.ollama.internal.model.OllamaChatRequest
import com.sibgear.deepseek.chat.data.ollama.internal.model.OllamaChatResponse
import com.sibgear.deepseek.chat.domain.model.AiRequestData
import com.sibgear.deepseek.chat.domain.model.ApiSettings
import com.sibgear.deepseek.chat.domain.model.ChatBranch
import com.sibgear.deepseek.chat.domain.model.ChatInvariant
import com.sibgear.deepseek.chat.domain.model.ChatMemoryItem
import com.sibgear.deepseek.chat.domain.model.ChatMemoryLayer
import com.sibgear.deepseek.chat.domain.model.ChatMessage
import com.sibgear.deepseek.chat.domain.model.ChatMessageAttachment
import com.sibgear.deepseek.chat.domain.model.ChatMessageFooter
import com.sibgear.deepseek.chat.domain.model.ChatMessageKind
import com.sibgear.deepseek.chat.domain.model.ChatRole
import com.sibgear.deepseek.chat.domain.model.ContextMessage
import com.sibgear.deepseek.chat.domain.model.StickyFact
import com.sibgear.deepseek.chat.domain.model.userApiContent
import com.sibgear.deepseek.chat.history.domain.model.HistoryBranch
import com.sibgear.deepseek.chat.history.domain.model.HistoryFact
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessage
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageAttachment
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageFooter
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageKind
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageMemoryMetadata
import com.sibgear.deepseek.chat.history.domain.model.HistoryRole

internal fun AiRequestData.toOllamaChatRequest(
    contextMessages: List<ContextMessage>,
    effectiveSystemPrompt: String = systemPrompt,
    servicePrompt: String? = null,
    stream: Boolean = false,
): OllamaChatRequest {
    val trimmedSystemPrompt = effectiveSystemPrompt.trim()
    return OllamaChatRequest(
        model = model.id,
        messages = buildList {
            if (trimmedSystemPrompt.isNotEmpty()) {
                add(OllamaChatMessage(role = "system", content = trimmedSystemPrompt))
            }
            contextMessages.forEach { message ->
                add(OllamaChatMessage(role = message.role.apiRole, content = message.content))
            }
            servicePrompt?.let { prompt ->
                add(OllamaChatMessage(role = "user", content = prompt))
            }
        },
        stream = stream,
        think = stream.takeIf { it },
        options = apiSettings.toOllamaOptions(),
    )
}

internal fun AiRequestData.toOllamaUserHistoryMessage(
    memory: HistoryMessageMemoryMetadata? = null,
): HistoryMessage =
    HistoryMessage(
        role = HistoryRole.User,
        content = prompt,
        apiContent = userApiContent(),
        attachment = attachment?.let {
            HistoryMessageAttachment(
                fileName = it.fileName,
                sizeBytes = it.sizeBytes,
            )
        },
        memory = memory,
    )

internal fun AiRequestData.toOllamaAssistantHistoryMessage(
    content: String,
    responseTimeMs: Long,
    response: OllamaChatResponse? = null,
    thinkingContent: String? = null,
): HistoryMessage {
    val promptTokens = response?.promptEvalCount
    val completionTokens = response?.evalCount
    return HistoryMessage(
        role = HistoryRole.Assistant,
        content = content,
        thinkingContent = thinkingContent?.takeIf { it.isNotBlank() },
        sourceLabel = "Ollama / ${model.displayName}",
        footer = HistoryMessageFooter(
            responseTimeMs = responseTimeMs,
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            totalTokens = listOfNotNull(promptTokens, completionTokens).takeIf { it.isNotEmpty() }?.sum(),
        ),
    )
}

internal fun List<HistoryMessage>.toChatMessages(): List<ChatMessage> =
    map { it.toChatMessage() }

internal fun List<HistoryMessage>.toContextMessages(): List<ContextMessage> =
    map { it.toContextMessage() }

internal fun List<HistoryFact>.toStickyFacts(): List<StickyFact> =
    map { fact ->
        StickyFact(
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

internal fun List<MemoryItem>.toChatMemoryItems(): List<ChatMemoryItem> =
    map { item ->
        ChatMemoryItem(
            id = item.id,
            layer = item.layer.toChatMemoryLayer(),
            fact = item.fact,
            importance = item.importance,
        )
    }

internal fun List<AssistantInvariant>.toChatInvariants(): List<ChatInvariant> =
    map { invariant ->
        ChatInvariant(
            category = invariant.category.storageValue,
            statement = invariant.statement,
            rationale = invariant.rationale,
            enabled = invariant.enabled,
        )
    }

private fun HistoryMessage.toContextMessage(): ContextMessage =
    ContextMessage(
        role = role.toChatRole(),
        kind = kind.toChatMessageKind(),
        content = apiContent ?: content,
        branchId = branchId,
    )

private fun HistoryMessage.toChatMessage(): ChatMessage =
    ChatMessage(
        role = role.toChatRole(),
        content = content,
        thinkingContent = thinkingContent,
        branchId = branchId,
        kind = kind.toChatMessageKind(),
        apiContent = apiContent,
        attachment = attachment?.let {
            ChatMessageAttachment(
                fileName = it.fileName,
                sizeBytes = it.sizeBytes,
            )
        },
        sourceLabel = sourceLabel,
        footer = footer?.let {
            ChatMessageFooter(
                responseTimeMs = it.responseTimeMs,
                promptTokens = it.promptTokens,
                completionTokens = it.completionTokens,
                totalTokens = it.totalTokens,
                cost = it.cost,
                retryCount = it.retryCount,
            )
        },
    )

private fun HistoryRole.toChatRole(): ChatRole =
    when (this) {
        HistoryRole.User -> ChatRole.User
        HistoryRole.Assistant -> ChatRole.Assistant
    }

private fun HistoryMessageKind.toChatMessageKind(): ChatMessageKind =
    when (this) {
        HistoryMessageKind.Regular -> ChatMessageKind.Regular
        HistoryMessageKind.CompressionSummary -> ChatMessageKind.CompressionSummary
        HistoryMessageKind.TaskStateEvent -> ChatMessageKind.TaskStateEvent
        HistoryMessageKind.RagDiagnostic -> ChatMessageKind.RagDiagnostic
    }

private fun MemoryLayer.toChatMemoryLayer(): ChatMemoryLayer =
    when (this) {
        MemoryLayer.ShortTerm -> ChatMemoryLayer.ShortTerm
        MemoryLayer.WorkingMemory -> ChatMemoryLayer.WorkingMemory
        MemoryLayer.LongTermMemory -> ChatMemoryLayer.LongTermMemory
    }

private val InvariantCategory.storageValue: String
    get() = when (this) {
        InvariantCategory.Architecture -> "architecture"
        InvariantCategory.TechnicalDecision -> "technical_decision"
        InvariantCategory.StackConstraint -> "stack_constraint"
        InvariantCategory.BusinessRule -> "business_rule"
        InvariantCategory.Process -> "process"
        InvariantCategory.Security -> "security"
        InvariantCategory.Other -> "other"
    }

private val ChatRole.apiRole: String
    get() = when (this) {
        ChatRole.User -> "user"
        ChatRole.Assistant -> "assistant"
    }

private fun ApiSettings.toOllamaOptions(): OllamaChatOptions? {
    if (!isApiControlEnabled) {
        return null
    }

    val stop = stopWord.trim().takeIf { it.isNotEmpty() }?.let { listOf(it) }
    return OllamaChatOptions(
        temperature = temperature.coerceIn(0f, 1f),
        numPredict = maxTokens.takeIf { it > 0 },
        numCtx = numCtx.takeIf { it > 0 },
        topP = topP.coerceIn(0f, 1f),
        seed = seed,
        repeatPenalty = repeatPenalty.takeIf { it > 0f },
        stop = stop,
    )
}
