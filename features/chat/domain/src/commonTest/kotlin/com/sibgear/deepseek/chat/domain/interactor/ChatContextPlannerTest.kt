package com.sibgear.deepseek.chat.domain.interactor

import com.sibgear.deepseek.chat.domain.model.ChatCompressionSettings
import com.sibgear.deepseek.chat.domain.model.ChatMessageKind
import com.sibgear.deepseek.chat.domain.model.ChatRole
import com.sibgear.deepseek.chat.domain.model.CompressionSummaryPrompt
import com.sibgear.deepseek.chat.domain.model.ContextMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ChatContextPlannerTest {
    private val planner = ChatContextPlanner()

    @Test
    fun compressionOffReturnsRegularMessagesOnly() {
        val plan = planner.plan(
            messages = listOf(
                message("old"),
                message("summary", kind = ChatMessageKind.CompressionSummary),
                message("new"),
            ),
            compressionSettings = ChatCompressionSettings(isEnabled = false, intervalMessages = 2),
        )

        assertEquals(listOf("old", "new"), plan.apiMessages.map { it.content })
        assertNull(plan.compressionRequest)
    }

    @Test
    fun compressionOnReturnsLastSummaryAndNewMessages() {
        val plan = planner.plan(
            messages = listOf(
                message("old"),
                message("summary", kind = ChatMessageKind.CompressionSummary),
                message("new"),
            ),
            compressionSettings = ChatCompressionSettings(isEnabled = true, intervalMessages = 2),
        )

        assertEquals(listOf("summary", "new"), plan.apiMessages.map { it.content })
    }

    @Test
    fun compressionRequestAppearsWhenRegularMessagesReachInterval() {
        val plan = planner.plan(
            messages = listOf(
                message("user"),
                message("assistant", role = ChatRole.Assistant),
            ),
            compressionSettings = ChatCompressionSettings(isEnabled = true, intervalMessages = 2),
        )

        val compressionRequest = assertNotNull(plan.compressionRequest)
        assertEquals(listOf("user", "assistant"), compressionRequest.messages.map { it.content })
        assertEquals(CompressionSummaryPrompt, compressionRequest.prompt)
    }

    @Test
    fun nextCompressionRequestIncludesPreviousSummaryAndNewMessages() {
        val plan = planner.plan(
            messages = listOf(
                message("old"),
                message("summary", kind = ChatMessageKind.CompressionSummary),
                message("new user"),
                message("new assistant", role = ChatRole.Assistant),
            ),
            compressionSettings = ChatCompressionSettings(isEnabled = true, intervalMessages = 2),
        )

        val compressionRequest = assertNotNull(plan.compressionRequest)
        assertEquals(listOf("summary", "new user", "new assistant"), compressionRequest.messages.map { it.content })
    }

    @Test
    fun invalidIntervalIsCoercedToOne() {
        val plan = planner.plan(
            messages = listOf(message("user")),
            compressionSettings = ChatCompressionSettings(isEnabled = true, intervalMessages = 0),
        )

        assertNotNull(plan.compressionRequest)
    }

    private fun message(
        content: String,
        role: ChatRole = ChatRole.User,
        kind: ChatMessageKind = ChatMessageKind.Regular,
    ): ContextMessage =
        ContextMessage(
            role = role,
            kind = kind,
            content = content,
        )
}
