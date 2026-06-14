package com.sibgear.deepseek.chat.data.deepseek.internal.mapper

import com.sibgear.deepseek.chat.data.deepseek.internal.model.DeepSeekApiChatMessage
import com.sibgear.deepseek.chat.data.deepseek.internal.model.DeepSeekChatCompletionRequest
import com.sibgear.deepseek.chat.data.deepseek.internal.model.DeepSeekResponseUsage
import com.sibgear.deepseek.chat.data.deepseek.internal.model.DeepSeekThinking
import com.sibgear.deepseek.chat.data.deepseek.internal.model.deepSeekCost
import com.sibgear.deepseek.chat.domain.model.AiRequestData
import com.sibgear.deepseek.chat.domain.model.ApiSettings
import com.sibgear.deepseek.chat.domain.model.ChatMessage
import com.sibgear.deepseek.chat.domain.model.ChatMessageAttachment
import com.sibgear.deepseek.chat.domain.model.ChatMessageFooter
import com.sibgear.deepseek.chat.domain.model.ChatMessageKind
import com.sibgear.deepseek.chat.domain.model.ChatRole
import com.sibgear.deepseek.chat.domain.model.ContextMessage
import com.sibgear.deepseek.chat.domain.model.StickyFact
import com.sibgear.deepseek.chat.domain.model.userApiContent
import com.sibgear.deepseek.chat.history.domain.model.HistoryFact
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessage
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageAttachment
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageFooter
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageKind
import com.sibgear.deepseek.chat.history.domain.model.HistoryRole

internal fun AiRequestData.toDeepSeekChatCompletionRequest(
    contextMessages: List<ContextMessage>,
    includeSystemPrompt: Boolean = true,
    servicePrompt: String? = null,
): DeepSeekChatCompletionRequest {
    val trimmedSystemPrompt = systemPrompt.trim()
    return DeepSeekChatCompletionRequest(
        model = model.id,
        messages = buildList {
            if (includeSystemPrompt && trimmedSystemPrompt.isNotEmpty()) {
                add(DeepSeekApiChatMessage(role = "system", content = trimmedSystemPrompt))
            }

            contextMessages.forEach { message ->
                add(DeepSeekApiChatMessage(role = message.role.apiRole, content = message.content))
            }

            servicePrompt?.let { prompt ->
                add(DeepSeekApiChatMessage(role = "user", content = prompt))
            }
        },
        stream = false,
        thinking = DeepSeekThinking(type = "disabled"),
        temperature = apiSettings.deepSeekTemperature(),
        maxTokens = apiSettings.deepSeekMaxTokens(),
        stop = apiSettings.deepSeekStop(),
    )
}

internal fun AiRequestData.toDeepSeekAssistantHistoryMessage(
    content: String,
    responseTimeMs: Long,
    usage: DeepSeekResponseUsage? = null,
): HistoryMessage =
    HistoryMessage(
        role = HistoryRole.Assistant,
        content = content,
        kind = HistoryMessageKind.Regular,
        sourceLabel = "DeepSeek / ${model.displayName}",
        footer = HistoryMessageFooter(
            responseTimeMs = responseTimeMs,
            promptTokens = usage?.promptTokens,
            completionTokens = usage?.completionTokens,
            totalTokens = usage?.displayTotalTokens,
            cost = usage?.deepSeekCost(model.id),
        ),
    )

internal fun AiRequestData.toDeepSeekCompressionSummaryHistoryMessage(
    content: String,
    responseTimeMs: Long,
    usage: DeepSeekResponseUsage? = null,
): HistoryMessage =
    HistoryMessage(
        role = HistoryRole.Assistant,
        content = content,
        kind = HistoryMessageKind.CompressionSummary,
        sourceLabel = "DeepSeek / ${model.displayName}",
        footer = HistoryMessageFooter(
            responseTimeMs = responseTimeMs,
            promptTokens = usage?.promptTokens,
            completionTokens = usage?.completionTokens,
            totalTokens = usage?.displayTotalTokens,
            cost = usage?.deepSeekCost(model.id),
        ),
    )

internal fun AiRequestData.toDeepSeekUserHistoryMessage(): HistoryMessage =
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
    )

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

internal fun List<StickyFact>.toHistoryFacts(): List<HistoryFact> =
    map { fact ->
        HistoryFact(
            key = fact.key,
            value = fact.value,
        )
    }

private fun HistoryMessage.toContextMessage(): ContextMessage =
    ContextMessage(
        role = role.toChatRole(),
        kind = kind.toChatMessageKind(),
        content = apiContent ?: content,
    )

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

private fun HistoryRole.toChatRole(): ChatRole =
    when (this) {
        HistoryRole.User -> ChatRole.User
        HistoryRole.Assistant -> ChatRole.Assistant
    }

private fun HistoryMessageKind.toChatMessageKind(): ChatMessageKind =
    when (this) {
        HistoryMessageKind.Regular -> ChatMessageKind.Regular
        HistoryMessageKind.CompressionSummary -> ChatMessageKind.CompressionSummary
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

private fun HistoryMessageAttachment.toChatMessageAttachment(): ChatMessageAttachment =
    ChatMessageAttachment(
        fileName = fileName,
        sizeBytes = sizeBytes,
    )

private val ChatRole.apiRole: String
    get() = when (this) {
        ChatRole.User -> "user"
        ChatRole.Assistant -> "assistant"
    }

private fun ApiSettings.deepSeekTemperature(): Float? {
    if (!isApiControlEnabled) {
        return null
    }

    return temperature.coerceIn(0f, 1f) * 2f
}

private fun ApiSettings.deepSeekMaxTokens(): Int? {
    if (!isApiControlEnabled || maxTokens <= 0) {
        return null
    }

    return maxTokens
}

private fun ApiSettings.deepSeekStop(): List<String>? {
    if (!isApiControlEnabled) {
        return null
    }

    val trimmedStopWord = stopWord.trim()
    return trimmedStopWord.takeIf { it.isNotEmpty() }?.let { listOf(it) }
}
