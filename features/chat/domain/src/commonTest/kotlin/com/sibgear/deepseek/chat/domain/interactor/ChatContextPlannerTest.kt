package com.sibgear.deepseek.chat.domain.interactor

import com.sibgear.deepseek.chat.domain.model.ChatMessageKind
import com.sibgear.deepseek.chat.domain.model.ChatRole
import com.sibgear.deepseek.chat.domain.model.CompressionSummaryPrompt
import com.sibgear.deepseek.chat.domain.model.ContextManagementMode
import com.sibgear.deepseek.chat.domain.model.ContextManagementSettings
import com.sibgear.deepseek.chat.domain.model.ContextMessage
import com.sibgear.deepseek.chat.domain.model.StickyFact
import com.sibgear.deepseek.chat.domain.model.StickyFactsUpdatePrompt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ChatContextPlannerTest {
    private val planner = ChatContextPlanner()

    @Test
    fun noneReturnsRegularMessagesOnly() {
        val plan = planner.plan(
            messages = listOf(
                message("old"),
                message("summary", kind = ChatMessageKind.CompressionSummary),
                message("new"),
            ),
            contextManagementSettings = ContextManagementSettings(mode = ContextManagementMode.None),
        )

        assertEquals(listOf("old", "new"), plan.apiMessages.map { it.content })
        assertNull(plan.compressionRequest)
    }

    @Test
    fun contextSummaryReturnsLastSummaryAndNewMessages() {
        val plan = planner.plan(
            messages = listOf(
                message("old"),
                message("summary", kind = ChatMessageKind.CompressionSummary),
                message("new"),
            ),
            contextManagementSettings = ContextManagementSettings(
                mode = ContextManagementMode.ContextSummary,
                summaryIntervalMessages = 2,
            ),
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
            contextManagementSettings = ContextManagementSettings(
                mode = ContextManagementMode.ContextSummary,
                summaryIntervalMessages = 2,
            ),
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
            contextManagementSettings = ContextManagementSettings(
                mode = ContextManagementMode.ContextSummary,
                summaryIntervalMessages = 2,
            ),
        )

        val compressionRequest = assertNotNull(plan.compressionRequest)
        assertEquals(listOf("summary", "new user", "new assistant"), compressionRequest.messages.map { it.content })
    }

    @Test
    fun invalidSummaryIntervalIsCoercedToOne() {
        val plan = planner.plan(
            messages = listOf(message("user")),
            contextManagementSettings = ContextManagementSettings(
                mode = ContextManagementMode.ContextSummary,
                summaryIntervalMessages = 0,
            ),
        )

        assertNotNull(plan.compressionRequest)
    }

    @Test
    fun slidingWindowReturnsLastRegularMessagesOnly() {
        val plan = planner.plan(
            messages = listOf(
                message("first"),
                message("second"),
                message("third", role = ChatRole.Assistant),
            ),
            contextManagementSettings = ContextManagementSettings(
                mode = ContextManagementMode.SlidingWindow,
                slidingWindowMessages = 2,
            ),
        )

        assertEquals(listOf("second", "third"), plan.apiMessages.map { it.content })
        assertNull(plan.compressionRequest)
    }

    @Test
    fun slidingWindowIgnoresCompressionSummaryMessages() {
        val plan = planner.plan(
            messages = listOf(
                message("first"),
                message("summary", kind = ChatMessageKind.CompressionSummary),
                message("second"),
                message("third", role = ChatRole.Assistant),
            ),
            contextManagementSettings = ContextManagementSettings(
                mode = ContextManagementMode.SlidingWindow,
                slidingWindowMessages = 2,
            ),
        )

        assertEquals(listOf("second", "third"), plan.apiMessages.map { it.content })
    }

    @Test
    fun invalidSlidingWindowSizeIsCoercedToOne() {
        val plan = planner.plan(
            messages = listOf(
                message("first"),
                message("second"),
            ),
            contextManagementSettings = ContextManagementSettings(
                mode = ContextManagementMode.SlidingWindow,
                slidingWindowMessages = 0,
            ),
        )

        assertEquals(listOf("second"), plan.apiMessages.map { it.content })
    }

    @Test
    fun stickyFactsReturnsFactsMessageFirstAndLastRegularMessages() {
        val plan = planner.plan(
            messages = listOf(
                message("old"),
                message("summary", kind = ChatMessageKind.CompressionSummary),
                message("kept", role = ChatRole.Assistant),
            ),
            contextManagementSettings = ContextManagementSettings(
                mode = ContextManagementMode.StickyFacts,
                stickyFactsWindowMessages = 1,
            ),
            stickyFacts = listOf(StickyFact(key = "goal", value = "test")),
        )

        assertEquals(
            listOf(
                "Sticky facts:\n- goal: test",
                "kept",
            ),
            plan.apiMessages.map { it.content },
        )
        assertEquals(ChatRole.User, plan.apiMessages.first().role)
    }

    @Test
    fun stickyFactsUpdateRequestUsesFactsAndLastUserAssistantPair() {
        val plan = planner.plan(
            messages = listOf(
                message("first user"),
                message("assistant", role = ChatRole.Assistant),
                message("last user"),
                message("last assistant", role = ChatRole.Assistant),
            ),
            contextManagementSettings = ContextManagementSettings(mode = ContextManagementMode.StickyFacts),
            stickyFacts = listOf(StickyFact(key = "goal", value = "test")),
        )

        val updateRequest = assertNotNull(plan.stickyFactsUpdateRequest)
        assertEquals(
            listOf(
                "Sticky facts:\n- goal: test",
                "last user",
                "last assistant",
            ),
            updateRequest.messages.map { it.content },
        )
        assertEquals(StickyFactsUpdatePrompt, updateRequest.prompt)
    }

    @Test
    fun stickyFactsUpdateRequestIsNotCreatedBeforeAssistantResponse() {
        val plan = planner.plan(
            messages = listOf(
                message("first user"),
                message("assistant", role = ChatRole.Assistant),
                message("last user"),
            ),
            contextManagementSettings = ContextManagementSettings(mode = ContextManagementMode.StickyFacts),
            stickyFacts = listOf(StickyFact(key = "goal", value = "test")),
        )

        assertNull(plan.stickyFactsUpdateRequest)
    }

    @Test
    fun stickyFactsUpdateRequestIgnoresCompressionSummary() {
        val plan = planner.plan(
            messages = listOf(
                message("first user"),
                message("summary", kind = ChatMessageKind.CompressionSummary),
                message("assistant", role = ChatRole.Assistant),
            ),
            contextManagementSettings = ContextManagementSettings(mode = ContextManagementMode.StickyFacts),
        )

        val updateRequest = assertNotNull(plan.stickyFactsUpdateRequest)
        assertEquals(listOf("first user", "assistant"), updateRequest.messages.map { it.content })
    }

    @Test
    fun invalidStickyFactsWindowSizeIsCoercedToOne() {
        val plan = planner.plan(
            messages = listOf(
                message("first"),
                message("second"),
            ),
            contextManagementSettings = ContextManagementSettings(
                mode = ContextManagementMode.StickyFacts,
                stickyFactsWindowMessages = 0,
            ),
        )

        assertEquals(listOf("second"), plan.apiMessages.map { it.content })
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
