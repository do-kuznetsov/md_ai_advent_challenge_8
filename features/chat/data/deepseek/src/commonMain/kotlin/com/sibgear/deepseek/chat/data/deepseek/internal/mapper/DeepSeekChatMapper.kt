package com.sibgear.deepseek.chat.data.deepseek.internal.mapper

import com.sibgear.deepseek.chat.data.deepseek.internal.model.DeepSeekApiChatMessage
import com.sibgear.deepseek.chat.data.deepseek.internal.model.DeepSeekChatCompletionRequest
import com.sibgear.deepseek.chat.data.deepseek.internal.model.DeepSeekResponseUsage
import com.sibgear.deepseek.chat.data.deepseek.internal.model.DeepSeekThinking
import com.sibgear.deepseek.chat.data.deepseek.internal.model.deepSeekCost
import com.sibgear.deepseek.chat.domain.model.AiRequestData
import com.sibgear.deepseek.chat.domain.model.ApiSettings
import com.sibgear.deepseek.chat.domain.model.ChatMessage
import com.sibgear.deepseek.chat.domain.model.ChatMessageFooter
import com.sibgear.deepseek.chat.domain.model.ChatRole
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessage
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageFooter
import com.sibgear.deepseek.chat.history.domain.model.HistoryRole

internal fun AiRequestData.toDeepSeekChatCompletionRequest(
    historyMessages: List<HistoryMessage>,
): DeepSeekChatCompletionRequest {
    val trimmedSystemPrompt = systemPrompt.trim()
    return DeepSeekChatCompletionRequest(
        model = model.id,
        messages = buildList {
            if (trimmedSystemPrompt.isNotEmpty()) {
                add(DeepSeekApiChatMessage(role = "system", content = trimmedSystemPrompt))
            }

            historyMessages.forEach { message ->
                add(DeepSeekApiChatMessage(role = message.role.apiRole, content = message.content))
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
        sourceLabel = "DeepSeek / ${model.displayName}",
        footer = HistoryMessageFooter(
            responseTimeMs = responseTimeMs,
            promptTokens = usage?.promptTokens,
            completionTokens = usage?.completionTokens,
            totalTokens = usage?.displayTotalTokens,
            cost = usage?.deepSeekCost(model.id),
        ),
    )

internal fun List<HistoryMessage>.toChatMessages(): List<ChatMessage> =
    map { it.toChatMessage() }

private fun HistoryMessage.toChatMessage(): ChatMessage =
    ChatMessage(
        role = role.toChatRole(),
        content = content,
        sourceLabel = sourceLabel,
        footer = footer?.toChatMessageFooter(),
    )

private fun HistoryRole.toChatRole(): ChatRole =
    when (this) {
        HistoryRole.User -> ChatRole.User
        HistoryRole.Assistant -> ChatRole.Assistant
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

private val HistoryRole.apiRole: String
    get() = when (this) {
        HistoryRole.User -> "user"
        HistoryRole.Assistant -> "assistant"
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
