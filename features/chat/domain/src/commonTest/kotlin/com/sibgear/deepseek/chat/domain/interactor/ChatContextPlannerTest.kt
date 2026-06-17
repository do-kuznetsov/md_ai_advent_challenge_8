package com.sibgear.deepseek.chat.domain.interactor

import com.sibgear.deepseek.chat.domain.model.BranchRoutingDecision
import com.sibgear.deepseek.chat.domain.model.BranchSummaryUpdatePrompt
import com.sibgear.deepseek.chat.domain.model.ChatBranch
import com.sibgear.deepseek.chat.domain.model.ChatMemoryItem
import com.sibgear.deepseek.chat.domain.model.ChatMemoryLayer
import com.sibgear.deepseek.chat.domain.model.ChatMemoryRetrievalPlan
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

    @Test
    fun branchingReturnsMessagesFromActiveBranchAndAncestorsOnly() {
        val branches = listOf(
            ChatBranch(id = 1, title = "tech", summary = "tech"),
            ChatBranch(id = 2, parentId = 1, title = "cars", summary = "cars"),
            ChatBranch(id = 3, parentId = 2, title = "sports", summary = "sports"),
            ChatBranch(id = 4, parentId = 2, title = "pickups", summary = "pickups"),
        )

        val plan = planner.plan(
            messages = listOf(
                message("tech user", branchId = 1),
                message("cars assistant", role = ChatRole.Assistant, branchId = 2),
                message("sports user", branchId = 3),
                message("pickups sibling", branchId = 4),
                message("old unbranched"),
            ),
            contextManagementSettings = ContextManagementSettings(mode = ContextManagementMode.Branching),
            branches = branches,
            activeBranchId = 3,
        )

        assertEquals(listOf("tech user", "cars assistant", "sports user"), plan.apiMessages.map { it.content })
    }

    @Test
    fun branchingWithoutActiveBranchFallsBackToRegularMessages() {
        val plan = planner.plan(
            messages = listOf(
                message("first"),
                message("second", role = ChatRole.Assistant),
            ),
            contextManagementSettings = ContextManagementSettings(mode = ContextManagementMode.Branching),
            branches = emptyList(),
            activeBranchId = null,
        )

        assertEquals(listOf("first", "second"), plan.apiMessages.map { it.content })
    }

    @Test
    fun branchRoutingRequestContainsBranchTreeAndPrompt() {
        val request = planner.branchRoutingRequest(
            branches = listOf(
                ChatBranch(id = 1, title = "техника", summary = "про технику"),
                ChatBranch(id = 2, parentId = 1, title = "автомобили", summary = "про автомобили"),
            ),
            userPrompt = "Хочу обсудить пикапы",
        )

        assertEquals(true, request.prompt.contains("id=1; title=техника; summary=про технику"))
        assertEquals(true, request.prompt.contains("  - id=2; title=автомобили; summary=про автомобили"))
        assertEquals(true, request.prompt.contains("Хочу обсудить пикапы"))
    }

    @Test
    fun selectBranchCreatesNestedBranchForNewDecision() {
        val selection = planner.selectBranch(
            branches = listOf(ChatBranch(id = 1, title = "техника", summary = "про технику")),
            decision = BranchRoutingDecision.New(
                parentBranchId = 1,
                title = "автомобили",
                summary = "про автомобили",
            ),
        )

        assertEquals(2, selection.activeBranchId)
        assertEquals(
            ChatBranch(id = 2, parentId = 1, title = "автомобили", summary = "про автомобили"),
            selection.branches.last(),
        )
    }

    @Test
    fun branchSummaryUpdateRequestUsesLastUserAssistantPairFromActiveBranch() {
        val branches = listOf(ChatBranch(id = 1, title = "tech", summary = "old summary"))
        val plan = planner.plan(
            messages = listOf(
                message("sibling", branchId = 2),
                message("branch user", branchId = 1),
                message("branch assistant", role = ChatRole.Assistant, branchId = 1),
            ),
            contextManagementSettings = ContextManagementSettings(mode = ContextManagementMode.Branching),
            branches = branches,
            activeBranchId = 1,
        )

        val request = assertNotNull(plan.branchSummaryUpdateRequest)
        assertEquals(listOf("branch user", "branch assistant"), request.messages.map { it.content })
        assertEquals(true, request.prompt.contains("Текущая ветка: tech"))
        assertEquals(true, request.prompt.contains(BranchSummaryUpdatePrompt.trim()))
    }

    @Test
    fun memoryInjectionAddsSelectedMemoryToSystemPrompt() {
        val injection = planner.memoryInjection(
            originalSystemPrompt = "base system",
            retrievalPlan = ChatMemoryRetrievalPlan(
                needShortTerm = true,
                needWorkingMemory = true,
                needLongTermMemory = false,
            ),
            availableMemory = listOf(
                ChatMemoryItem(
                    id = "memory-1",
                    layer = ChatMemoryLayer.WorkingMemory,
                    fact = "Project uses Kotlin",
                    importance = 0.8,
                ),
                ChatMemoryItem(
                    id = "memory-2",
                    layer = ChatMemoryLayer.LongTermMemory,
                    fact = "User likes concise answers",
                    importance = 0.9,
                ),
            ),
        )

        assertEquals(listOf(ChatMemoryLayer.ShortTerm, ChatMemoryLayer.WorkingMemory), injection.usedLayers)
        assertEquals(listOf("memory-1"), injection.injectedItems.map { it.id })
        assertEquals(true, injection.effectiveSystemPrompt.contains("[MEMORY_CONTEXT]"))
        assertEquals(true, injection.effectiveSystemPrompt.contains("Project uses Kotlin"))
        assertEquals(false, injection.effectiveSystemPrompt.contains("User likes concise answers"))
        assertEquals(false, injection.effectiveSystemPrompt.contains("Short-term memory"))
    }

    @Test
    fun memoryInjectionAddsUserProfileSeparatelyFromMemoryContext() {
        val injection = planner.memoryInjection(
            originalSystemPrompt = "base system",
            userProfile = "Стиль: отвечать кратко",
            retrievalPlan = ChatMemoryRetrievalPlan(),
            availableMemory = emptyList(),
        )

        assertEquals(true, injection.effectiveSystemPrompt.contains("[USER_PROFILE]"))
        assertEquals(true, injection.effectiveSystemPrompt.contains("Стиль: отвечать кратко"))
        assertEquals(false, injection.effectiveSystemPrompt.contains("[MEMORY_CONTEXT]"))
    }

    @Test
    fun memoryServicePromptsContainUserPromptAndAvailableMemory() {
        val classification = planner.memoryClassificationRequest("Use Kotlin and Compose")
        val retrieval = planner.memoryRetrievalRequest(
            userPrompt = "Continue the task",
            availableMemory = listOf(
                ChatMemoryItem(
                    id = "memory-1",
                    layer = ChatMemoryLayer.WorkingMemory,
                    fact = "Use Compose",
                    importance = 0.7,
                ),
            ),
        )

        assertEquals(true, classification.prompt.contains("Use Kotlin and Compose"))
        assertEquals(true, retrieval.prompt.contains("memory-1"))
        assertEquals(true, retrieval.prompt.contains("Use Compose"))
    }

    private fun message(
        content: String,
        role: ChatRole = ChatRole.User,
        kind: ChatMessageKind = ChatMessageKind.Regular,
        branchId: Int? = null,
    ): ContextMessage =
        ContextMessage(
            role = role,
            kind = kind,
            content = content,
            branchId = branchId,
        )
}
