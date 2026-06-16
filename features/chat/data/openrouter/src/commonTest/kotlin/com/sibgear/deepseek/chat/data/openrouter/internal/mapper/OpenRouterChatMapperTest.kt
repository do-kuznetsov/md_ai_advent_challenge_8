package com.sibgear.deepseek.chat.data.openrouter.internal.mapper

import com.sibgear.deepseek.chat.domain.model.AiModel
import com.sibgear.deepseek.chat.domain.model.AiProvider
import com.sibgear.deepseek.chat.domain.model.AiRequestData
import com.sibgear.deepseek.chat.domain.model.ApiSettings
import com.sibgear.deepseek.chat.domain.model.BranchRoutingDecision
import com.sibgear.deepseek.chat.domain.model.ChatMemoryCandidate
import com.sibgear.deepseek.chat.domain.model.ChatMemoryLayer
import com.sibgear.deepseek.chat.domain.model.ChatMessageKind
import com.sibgear.deepseek.assistant.memory.domain.model.MemoryLayer
import com.sibgear.deepseek.assistant.memory.domain.model.MemoryUpdate
import com.sibgear.deepseek.assistant.memory.domain.model.MemoryUpdateAction
import com.sibgear.deepseek.chat.domain.model.ContextManagementMode
import com.sibgear.deepseek.chat.domain.model.ContextManagementSettings
import com.sibgear.deepseek.chat.domain.interactor.ChatContextPlanner
import com.sibgear.deepseek.chat.domain.model.StickyFact
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessage
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageKind
import com.sibgear.deepseek.chat.history.domain.model.HistoryRole
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class OpenRouterChatMapperTest {
    @Test
    fun usesApiContentWhenHistoryMessageHasHiddenAttachmentText() {
        val request = AiRequestData(
            systemPrompt = "",
            prompt = "visible prompt",
            model = AiModel(id = "openrouter/test", provider = AiProvider.OpenRouter),
            apiSettings = ApiSettings(),
        )
        val history = listOf(
            HistoryMessage(
                role = HistoryRole.User,
                content = "visible prompt",
                apiContent = "visible prompt\n\nhidden file text",
            ),
        )

        val apiRequest = request.toOpenRouterChatCompletionRequest(history.toContextMessages())

        assertEquals("visible prompt\n\nhidden file text", apiRequest.messages.single().content)
    }

    @Test
    fun mapsHistoryMessageKindToContextMessageKind() {
        val history = listOf(
            HistoryMessage(
                role = HistoryRole.Assistant,
                content = "summary",
                kind = HistoryMessageKind.CompressionSummary,
            ),
        )

        val contextMessages = history.toContextMessages()

        assertEquals("summary", contextMessages.single().content)
        assertEquals(ChatMessageKind.CompressionSummary, contextMessages.single().kind)
    }

    @Test
    fun requestBodyUsesOnlyPlannedContextMessages() {
        val request = AiRequestData(
            systemPrompt = "",
            prompt = "visible prompt",
            model = AiModel(id = "openrouter/test", provider = AiProvider.OpenRouter),
            apiSettings = ApiSettings(),
            contextManagementSettings = ContextManagementSettings(
                mode = ContextManagementMode.SlidingWindow,
                slidingWindowMessages = 1,
            ),
        )
        val history = listOf(
            HistoryMessage(role = HistoryRole.User, content = "old"),
            HistoryMessage(role = HistoryRole.Assistant, content = "kept"),
        )
        val plannedContext = ChatContextPlanner().plan(
            messages = history.toContextMessages(),
            contextManagementSettings = request.contextManagementSettings,
        ).apiMessages

        val apiRequest = request.toOpenRouterChatCompletionRequest(plannedContext)

        assertEquals(listOf("kept"), apiRequest.messages.map { it.content })
    }

    @Test
    fun requestBodyKeepsSystemPromptSeparateFromStickyFactsMessage() {
        val request = AiRequestData(
            systemPrompt = "system",
            prompt = "visible prompt",
            model = AiModel(id = "openrouter/free", provider = AiProvider.OpenRouter),
            apiSettings = ApiSettings(),
            contextManagementSettings = ContextManagementSettings(
                mode = ContextManagementMode.StickyFacts,
                stickyFactsWindowMessages = 1,
            ),
        )
        val plannedContext = ChatContextPlanner().plan(
            messages = listOf(HistoryMessage(role = HistoryRole.User, content = "visible prompt")).toContextMessages(),
            contextManagementSettings = request.contextManagementSettings,
            stickyFacts = listOf(StickyFact(key = "goal", value = "test")),
        ).apiMessages

        val apiRequest = request.toOpenRouterChatCompletionRequest(plannedContext)

        assertEquals(listOf("system", "user", "user"), apiRequest.messages.map { it.role })
        assertEquals("system", apiRequest.messages[0].content)
        assertEquals("Sticky facts:\n- goal: test", apiRequest.messages[1].content)
        assertEquals("visible prompt", apiRequest.messages[2].content)
    }

    @Test
    fun parsesExistingBranchRoutingDecision() {
        val decision = """{"type":"existing","branchId":3}""".toBranchRoutingDecision(Json)

        assertEquals(BranchRoutingDecision.Existing(branchId = 3), decision)
    }

    @Test
    fun parsesNewBranchRoutingDecision() {
        val decision = """
            ```json
            {"type":"new","parentBranchId":2,"title":"sports","summary":"sport cars"}
            ```
        """.trimIndent().toBranchRoutingDecision(Json)

        assertEquals(
            BranchRoutingDecision.New(
                parentBranchId = 2,
                title = "sports",
                summary = "sport cars",
            ),
            decision,
        )
    }

    @Test
    fun parsesMemoryClassificationCandidates() {
        val candidates = """
            ```json
            {"store":true,"memory_items":[{"layer":"working_memory","fact":"Project uses Kotlin","importance":0.8}]}
            ```
        """.trimIndent().toMemoryCandidates(Json)

        assertEquals(
            listOf(
                ChatMemoryCandidate(
                    layer = ChatMemoryLayer.WorkingMemory,
                    fact = "Project uses Kotlin",
                    importance = 0.8,
                ),
            ),
            candidates,
        )
    }

    @Test
    fun parsesMemoryUpdates() {
        val updates = """{"updates":[{"action":"update","id":"memory-1","layer":"long_term_memory","fact":"Concise","importance":0.9}]}"""
            .toMemoryUpdates(Json)

        assertEquals(
            listOf(
                MemoryUpdate(
                    action = MemoryUpdateAction.Update,
                    id = "memory-1",
                    layer = MemoryLayer.LongTermMemory,
                    fact = "Concise",
                    importance = 0.9,
                ),
            ),
            updates,
        )
    }

    @Test
    fun parsesMemoryRetrievalPlan() {
        val plan = """{"need_short_term":true,"need_working_memory":true,"need_long_term_memory":false,"memory_ids":["memory-1"],"reason":"relevant"}"""
            .toChatMemoryRetrievalPlan(Json)

        assertEquals(true, plan?.needShortTerm)
        assertEquals(true, plan?.needWorkingMemory)
        assertEquals(false, plan?.needLongTermMemory)
        assertEquals(listOf("memory-1"), plan?.memoryItemIds)
    }
}
