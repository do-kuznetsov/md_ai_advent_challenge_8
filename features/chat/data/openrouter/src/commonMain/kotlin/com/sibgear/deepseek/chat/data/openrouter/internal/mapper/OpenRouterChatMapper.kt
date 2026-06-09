package com.sibgear.deepseek.chat.data.openrouter.internal.mapper

import com.sibgear.deepseek.chat.data.openrouter.internal.model.OpenRouterApiChatMessage
import com.sibgear.deepseek.chat.data.openrouter.internal.model.OpenRouterChatCompletionRequest
import com.sibgear.deepseek.chat.data.openrouter.internal.model.OpenRouterResponseUsage
import com.sibgear.deepseek.chat.domain.model.AiRequestData
import com.sibgear.deepseek.chat.domain.model.ApiSettings
import com.sibgear.deepseek.chat.domain.model.ChatMessage
import com.sibgear.deepseek.chat.domain.model.ChatMessageFooter
import com.sibgear.deepseek.chat.domain.model.ChatRole
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessage
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageFooter
import com.sibgear.deepseek.chat.history.domain.model.HistoryRole

internal fun AiRequestData.toOpenRouterChatCompletionRequest(
    historyMessages: List<HistoryMessage>,
): OpenRouterChatCompletionRequest {
    val trimmedSystemPrompt = systemPrompt.trim()
    return OpenRouterChatCompletionRequest(
        model = model.id,
        messages = buildList {
            if (trimmedSystemPrompt.isNotEmpty()) {
                add(OpenRouterApiChatMessage(role = "system", content = trimmedSystemPrompt))
            }

            historyMessages.forEach { message ->
                add(OpenRouterApiChatMessage(role = message.role.apiRole, content = message.content))
            }
        },
        stream = true,
        temperature = apiSettings.openRouterTemperature(),
        maxTokens = apiSettings.openRouterMaxTokens(),
        stop = apiSettings.openRouterStop(),
    )
}

internal fun AiRequestData.toOpenRouterAssistantHistoryMessage(
    content: String,
    responseTimeMs: Long,
    usage: OpenRouterResponseUsage? = null,
    retryCount: Int = 0,
): HistoryMessage =
    HistoryMessage(
        role = HistoryRole.Assistant,
        content = content,
        sourceLabel = "OpenRouter / ${model.displayName}",
        footer = HistoryMessageFooter(
            responseTimeMs = responseTimeMs,
            promptTokens = usage?.promptTokens,
            completionTokens = usage?.completionTokens,
            totalTokens = usage?.displayTotalTokens,
            cost = usage?.cost,
            retryCount = retryCount,
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

private fun ApiSettings.openRouterTemperature(): Float? {
    if (!isApiControlEnabled) {
        return null
    }

    return temperature.coerceIn(0f, 1f) * 2f
}

private fun ApiSettings.openRouterMaxTokens(): Int? {
    if (!isApiControlEnabled || maxTokens <= 0) {
        return null
    }

    return maxTokens
}

private fun ApiSettings.openRouterStop(): List<String>? {
    if (!isApiControlEnabled) {
        return null
    }

    val trimmedStopWord = stopWord.trim()
    return trimmedStopWord.takeIf { it.isNotEmpty() }?.let { listOf(it) }
}
