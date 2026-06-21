package com.sibgear.deepseek.chat.data.deepseek.external.service

import com.sibgear.deepseek.assistant.memory.domain.model.AssistantInvariant
import com.sibgear.deepseek.assistant.memory.domain.model.InvariantCategory
import com.sibgear.deepseek.assistant.memory.domain.model.InvariantCollectionMessage
import com.sibgear.deepseek.assistant.memory.domain.model.InvariantCollectionRole
import kotlin.test.Test
import kotlin.test.assertTrue

class DeepSeekAssistantProfileServiceTest {
    @Test
    fun invariantsPromptContainsCollectionChat() {
        val prompt = buildInvariantsUpdatePrompt(
            currentInvariants = listOf(
                AssistantInvariant(
                    id = "invariant-1",
                    category = InvariantCategory.Architecture,
                    statement = "Use layered architecture",
                ),
            ),
            chatMessages = listOf(
                InvariantCollectionMessage(
                    role = InvariantCollectionRole.Assistant,
                    text = "Какая архитектура обязательна?",
                ),
                InvariantCollectionMessage(
                    role = InvariantCollectionRole.User,
                    text = "Запрещено нарушать слои.",
                ),
            ),
        )

        assertTrue(prompt.contains("Диалог сбора инвариантов"))
        assertTrue(prompt.contains("Use layered architecture"))
        assertTrue(prompt.contains("assistant: Какая архитектура обязательна?"))
        assertTrue(prompt.contains("user: Запрещено нарушать слои."))
    }
}
