package com.sibgear.deepseek.chat.ui.internal.mapper

import com.sibgear.deepseek.chat.domain.model.AiModel
import com.sibgear.deepseek.chat.domain.model.ChatMessage
import com.sibgear.deepseek.chat.domain.model.ChatRole
import kotlin.math.roundToInt

internal fun buildContextUsageLabel(
    messages: List<ChatMessage>,
    selectedModel: AiModel,
): String {
    val totalTokens = selectedModel.contextLength ?: return UnknownContextLabel
    val usedTokens = messages
        .asReversed()
        .firstOrNull { message ->
            message.role == ChatRole.Assistant && message.footer?.promptTokens != null
        }
        ?.footer
        ?.let { footer ->
            footer.promptTokens.orZero() + footer.completionTokens.orZero()
        }
        ?: 0

    val percent = if (totalTokens > 0) {
        ((usedTokens.toDouble() / totalTokens.toDouble()) * 100)
            .roundToInt()
            .coerceIn(0, 100)
    } else {
        0
    }

    return "context: ${usedTokens.formatTokenCount()}/${totalTokens.formatTokenCount()} [$percent%]"
}

private fun Int?.orZero(): Int = this ?: 0

private fun Int.formatTokenCount(): String {
    val value = toString()
    return buildString {
        value.forEachIndexed { index, char ->
            if (index > 0 && (value.length - index) % TokenGroupSize == 0) {
                append(TokenGroupSeparator)
            }
            append(char)
        }
    }
}

private const val UnknownContextLabel = "context: unknown"
private const val TokenGroupSize = 3
private const val TokenGroupSeparator = '\''
