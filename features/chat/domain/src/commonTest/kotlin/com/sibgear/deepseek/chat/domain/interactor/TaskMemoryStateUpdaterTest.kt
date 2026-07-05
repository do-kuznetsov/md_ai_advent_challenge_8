package com.sibgear.deepseek.chat.domain.interactor

import com.sibgear.deepseek.chat.domain.model.ChatMessage
import com.sibgear.deepseek.chat.domain.model.ChatMessageKind
import com.sibgear.deepseek.chat.domain.model.ChatRole
import com.sibgear.deepseek.chat.domain.model.TaskMemoryState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TaskMemoryStateUpdaterTest {
    private val updater = TaskMemoryStateUpdater()

    @Test
    fun extractsGoalFromUserMessage() {
        val state = updater.update(
            TaskMemoryState(),
            "Цель: мигрировать feature-module promocodes-list на KMP",
        )

        assertEquals("мигрировать feature-module promocodes-list на KMP", state.goal)
    }

    @Test
    fun extractsClarifiedFacts() {
        val state = updater.update(
            TaskMemoryState(),
            "У нас модуль состоит из data, domain и presentation:ui",
        )

        assertEquals(
            listOf("У нас модуль состоит из data, domain и presentation:ui"),
            state.clarifiedFacts,
        )
    }

    @Test
    fun extractsConstraints() {
        val state = updater.update(
            TaskMemoryState(),
            "Ограничение: сначала нельзя трогать presentation:ui",
        )

        assertEquals(
            listOf("Ограничение: сначала нельзя трогать presentation:ui"),
            state.constraints,
        )
    }

    @Test
    fun extractsTerms() {
        val state = updater.update(
            TaskMemoryState(),
            "Термин: logic = presentation:logic",
        )

        assertEquals(mapOf("logic" to "presentation:logic"), state.terms)
    }

    @Test
    fun skipsDuplicateFacts() {
        val first = updater.update(
            TaskMemoryState(),
            "У нас модуль состоит из data и domain",
        )
        val second = updater.update(first, "У нас модуль состоит из data и domain")

        assertEquals(1, second.clarifiedFacts.size)
    }

    @Test
    fun replayUsesOnlyRegularUserMessages() {
        val state = updater.replay(
            listOf(
                ChatMessage(role = ChatRole.User, content = "Цель: мигрировать feature-module на KMP"),
                ChatMessage(role = ChatRole.Assistant, content = "Ограничение: не из assistant"),
                ChatMessage(
                    role = ChatRole.Assistant,
                    content = "RAG rewrite",
                    kind = ChatMessageKind.RagDiagnostic,
                ),
                ChatMessage(role = ChatRole.User, content = "Термин: logic = presentation:logic"),
            ),
        )

        assertEquals("мигрировать feature-module на KMP", state.goal)
        assertEquals(mapOf("logic" to "presentation:logic"), state.terms)
        assertFalse(state.constraints.any { it.contains("assistant") })
    }
}
