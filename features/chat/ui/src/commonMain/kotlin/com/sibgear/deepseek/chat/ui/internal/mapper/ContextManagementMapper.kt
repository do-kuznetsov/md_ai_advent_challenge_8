package com.sibgear.deepseek.chat.ui.internal.mapper

import com.sibgear.deepseek.chat.domain.model.ChatMessage
import com.sibgear.deepseek.chat.domain.model.ChatMessageKind
import com.sibgear.deepseek.chat.domain.model.ContextManagementMode
import com.sibgear.deepseek.chat.domain.model.DefaultContextManagementMessages

internal fun buildPinnedContextMessageIndex(
    messages: List<ChatMessage>,
    mode: ContextManagementMode,
    slidingWindowMessagesInput: String,
): Int? {
    if (mode != ContextManagementMode.SlidingWindow) {
        return null
    }

    val windowSize = slidingWindowMessagesInput.slidingWindowSize()
    val regularMessageIndexes = messages.regularMessageIndexes()

    if (regularMessageIndexes.size <= windowSize) {
        return null
    }

    return regularMessageIndexes.takeLast(windowSize).firstOrNull()
}

private fun String.slidingWindowSize(): Int =
    toIntOrNull()
        ?.coerceAtLeast(1)
        ?: DefaultContextManagementMessages

private fun List<ChatMessage>.regularMessageIndexes(): List<Int> =
    withIndex()
        .filter { (_, message) -> message.kind == ChatMessageKind.Regular }
        .map { it.index }
