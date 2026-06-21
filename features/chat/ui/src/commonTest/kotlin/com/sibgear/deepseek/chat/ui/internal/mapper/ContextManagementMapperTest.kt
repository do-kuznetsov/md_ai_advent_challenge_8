package com.sibgear.deepseek.chat.ui.internal.mapper

import com.sibgear.deepseek.chat.domain.model.ChatMessage
import com.sibgear.deepseek.chat.domain.model.ChatMessageKind
import com.sibgear.deepseek.chat.domain.model.ChatRole
import com.sibgear.deepseek.chat.domain.model.ContextManagementMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContextManagementMapperTest {
    @Test
    fun slidingWindowReturnsBoundaryIndexWhenMessagesExceedWindow() {
        val messages = listOf(
            message("first"),
            message("second"),
            message("third"),
        )

        val index = buildPinnedContextMessageIndex(
            messages = messages,
            mode = ContextManagementMode.SlidingWindow,
            slidingWindowMessagesInput = "2",
        )

        assertEquals(1, index)
    }

    @Test
    fun slidingWindowDoesNotReturnBoundaryWhenMessagesFitWindow() {
        val index = buildPinnedContextMessageIndex(
            messages = listOf(message("first"), message("second")),
            mode = ContextManagementMode.SlidingWindow,
            slidingWindowMessagesInput = "2",
        )

        assertNull(index)
    }

    @Test
    fun slidingWindowIgnoresCompressionSummaryWhenCalculatingBoundary() {
        val messages = listOf(
            message("summary", kind = ChatMessageKind.CompressionSummary),
            message("first"),
            message("second"),
            message("third"),
        )

        val index = buildPinnedContextMessageIndex(
            messages = messages,
            mode = ContextManagementMode.SlidingWindow,
            slidingWindowMessagesInput = "2",
        )

        assertEquals(2, index)
    }

    @Test
    fun slidingWindowIgnoresTaskStateEventsWhenCalculatingBoundary() {
        val messages = listOf(
            message("task event", kind = ChatMessageKind.TaskStateEvent),
            message("first"),
            message("second"),
            message("third"),
        )

        val index = buildPinnedContextMessageIndex(
            messages = messages,
            mode = ContextManagementMode.SlidingWindow,
            slidingWindowMessagesInput = "2",
        )

        assertEquals(2, index)
    }

    private fun message(
        content: String,
        kind: ChatMessageKind = ChatMessageKind.Regular,
    ): ChatMessage =
        ChatMessage(
            role = ChatRole.User,
            content = content,
            kind = kind,
        )
}
