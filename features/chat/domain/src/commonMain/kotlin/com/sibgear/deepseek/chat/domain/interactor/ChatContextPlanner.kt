package com.sibgear.deepseek.chat.domain.interactor

import com.sibgear.deepseek.chat.domain.model.BranchRoutingDecision
import com.sibgear.deepseek.chat.domain.model.BranchRoutingRequest
import com.sibgear.deepseek.chat.domain.model.BranchSelection
import com.sibgear.deepseek.chat.domain.model.BranchSummaryUpdatePrompt
import com.sibgear.deepseek.chat.domain.model.BranchSummaryUpdateRequest
import com.sibgear.deepseek.chat.domain.model.ChatBranch
import com.sibgear.deepseek.chat.domain.model.ChatMemoryCandidate
import com.sibgear.deepseek.chat.domain.model.ChatMemoryItem
import com.sibgear.deepseek.chat.domain.model.ChatMemoryLayer
import com.sibgear.deepseek.chat.domain.model.ChatMemoryRetrievalPlan
import com.sibgear.deepseek.chat.domain.model.ChatMessageKind
import com.sibgear.deepseek.chat.domain.model.ChatRole
import com.sibgear.deepseek.chat.domain.model.ContextManagementMode
import com.sibgear.deepseek.chat.domain.model.ContextManagementSettings
import com.sibgear.deepseek.chat.domain.model.CompressionRequest
import com.sibgear.deepseek.chat.domain.model.CompressionSummaryPrompt
import com.sibgear.deepseek.chat.domain.model.ContextMessage
import com.sibgear.deepseek.chat.domain.model.ContextPlan
import com.sibgear.deepseek.chat.domain.model.MemoryClassificationRequest
import com.sibgear.deepseek.chat.domain.model.MemoryInjection
import com.sibgear.deepseek.chat.domain.model.MemoryMutationRequest
import com.sibgear.deepseek.chat.domain.model.MemoryRetrievalRequest
import com.sibgear.deepseek.chat.domain.model.StickyFact
import com.sibgear.deepseek.chat.domain.model.StickyFactsUpdatePrompt
import com.sibgear.deepseek.chat.domain.model.StickyFactsUpdateRequest

class ChatContextPlanner {
    fun plan(
        messages: List<ContextMessage>,
        contextManagementSettings: ContextManagementSettings,
        stickyFacts: List<StickyFact> = emptyList(),
        branches: List<ChatBranch> = emptyList(),
        activeBranchId: Int? = null,
    ): ContextPlan {
        val apiMessages = when (contextManagementSettings.mode) {
            ContextManagementMode.None -> messages.regularMessages()
            ContextManagementMode.ContextSummary -> messages.activeContextMessages()
            ContextManagementMode.SlidingWindow -> messages.regularMessages()
                .takeLast(contextManagementSettings.slidingWindowMessages.coerceAtLeast(1))
            ContextManagementMode.StickyFacts -> stickyFacts.toContextMessages() + messages.regularMessages()
                .takeLast(contextManagementSettings.stickyFactsWindowMessages.coerceAtLeast(1))
            ContextManagementMode.Branching -> messages.branchPathMessages(
                branches = branches,
                activeBranchId = activeBranchId,
            )
        }

        val compressionRequest = if (contextManagementSettings.mode == ContextManagementMode.ContextSummary &&
            messages.shouldCompress(contextManagementSettings)
        ) {
            CompressionRequest(
                messages = apiMessages,
                prompt = CompressionSummaryPrompt,
            )
        } else {
            null
        }

        val stickyFactsUpdateRequest = if (contextManagementSettings.mode == ContextManagementMode.StickyFacts) {
            messages.lastRegularUserAssistantPair()?.let { pairMessages ->
                StickyFactsUpdateRequest(
                    messages = stickyFacts.toContextMessages() + pairMessages,
                    prompt = StickyFactsUpdatePrompt,
                )
            }
        } else {
            null
        }

        return ContextPlan(
            apiMessages = apiMessages,
            compressionRequest = compressionRequest,
            stickyFactsUpdateRequest = stickyFactsUpdateRequest,
            branchSummaryUpdateRequest = branchSummaryUpdateRequest(
                messages = messages,
                branches = branches,
                activeBranchId = activeBranchId,
                contextManagementSettings = contextManagementSettings,
            ),
        )
    }

    fun branchRoutingRequest(
        branches: List<ChatBranch>,
        userPrompt: String,
    ): BranchRoutingRequest =
        BranchRoutingRequest(
            prompt = buildString {
                appendLine("Определи ветку диалога для нового сообщения пользователя.")
                appendLine()
                appendLine("Текущие ветки:")
                if (branches.isEmpty()) {
                    appendLine("- веток пока нет")
                } else {
                    branches.asTreeRows().forEach { row ->
                        appendLine("${"  ".repeat(row.depth)}- id=${row.branch.id}; title=${row.branch.title}; summary=${row.branch.summary}")
                    }
                }
                appendLine()
                appendLine("Новое сообщение пользователя:")
                appendLine(userPrompt)
                appendLine()
                appendLine("Верни только JSON object, без markdown и пояснений.")
                appendLine("""Для существующей ветки: {"type":"existing","branchId":3}""")
                appendLine("""Для новой ветки: {"type":"new","parentBranchId":2,"title":"...","summary":"..."}""")
                appendLine("Если тема новая верхнего уровня, parentBranchId должен быть null.")
            },
        )

    fun memoryClassificationRequest(userPrompt: String): MemoryClassificationRequest =
        MemoryClassificationRequest(
            prompt = buildString {
                appendLine("You are a memory classification engine.")
                appendLine()
                appendLine("Analyze the user message and determine whether factual information should be stored.")
                appendLine()
                appendLine("Memory layers:")
                appendLine("- working_memory: current task, project, technologies, constraints, objectives.")
                appendLine("- long_term_memory: stable user preferences, profile, long-lasting reusable facts.")
                appendLine()
                appendLine("Rules:")
                appendLine("- Extract only factual information.")
                appendLine("- Do not store requests, questions, or assistant responses.")
                appendLine("- Ignore trivial statements.")
                appendLine("- Return JSON only, without markdown.")
                appendLine()
                appendLine("Schema:")
                appendLine("""{"store":true,"memory_items":[{"layer":"working_memory","fact":"...","importance":0.7}]}""")
                appendLine()
                appendLine("User message:")
                append(userPrompt)
            },
        )

    fun memoryMutationRequest(
        currentMemory: List<ChatMemoryItem>,
        candidates: List<ChatMemoryCandidate>,
    ): MemoryMutationRequest =
        MemoryMutationRequest(
            prompt = buildString {
                appendLine("You are a memory update engine.")
                appendLine()
                appendLine("Update the stored memory using new candidate facts.")
                appendLine()
                appendLine("Rules:")
                appendLine("- Use add for new facts, update for corrected facts, delete for obsolete facts.")
                appendLine("- Preserve stable existing facts unless contradicted.")
                appendLine("- Do not create duplicates.")
                appendLine("- Never write short_term memory.")
                appendLine("- Return JSON only, without markdown.")
                appendLine()
                appendLine("Current memory:")
                appendMemoryItems(currentMemory)
                appendLine()
                appendLine("Candidate facts:")
                if (candidates.isEmpty()) {
                    appendLine("- none")
                } else {
                    candidates.forEach { candidate ->
                        appendLine("- layer=${candidate.layer.storageValue}; importance=${candidate.importance.coerceIn(0.0, 1.0)}; fact=${candidate.fact}")
                    }
                }
                appendLine()
                appendLine("Schema:")
                appendLine("""{"updates":[{"action":"add","layer":"working_memory","fact":"...","importance":0.7},{"action":"update","id":"memory-1","layer":"long_term_memory","fact":"...","importance":0.9},{"action":"delete","id":"memory-2"}]}""")
            },
        )

    fun memoryRetrievalRequest(
        userPrompt: String,
        availableMemory: List<ChatMemoryItem>,
    ): MemoryRetrievalRequest =
        MemoryRetrievalRequest(
            prompt = buildString {
                appendLine("You are a memory retrieval planner.")
                appendLine()
                appendLine("Your task is NOT to answer the user.")
                appendLine("Decide which memory should be provided to the main assistant.")
                appendLine()
                appendLine("Rules:")
                appendLine("- Select only memories relevant to the current user request.")
                appendLine("- Avoid irrelevant memories and minimize context size.")
                appendLine("- need_short_term means the recent conversation context is useful.")
                appendLine("- Return JSON only, without markdown.")
                appendLine()
                appendLine("Current user request:")
                appendLine(userPrompt)
                appendLine()
                appendLine("Available memory:")
                appendMemoryItems(availableMemory)
                appendLine()
                appendLine("Schema:")
                appendLine("""{"need_short_term":true,"need_working_memory":true,"need_long_term_memory":false,"memory_ids":["memory-1"],"reason":"..."}""")
            },
        )

    fun memoryInjection(
        originalSystemPrompt: String,
        userProfile: String = "",
        retrievalPlan: ChatMemoryRetrievalPlan,
        availableMemory: List<ChatMemoryItem>,
    ): MemoryInjection {
        val selectedItems = availableMemory.filter { item ->
            val layerSelected = when (item.layer) {
                ChatMemoryLayer.ShortTerm -> false
                ChatMemoryLayer.WorkingMemory -> retrievalPlan.needWorkingMemory
                ChatMemoryLayer.LongTermMemory -> retrievalPlan.needLongTermMemory
            }
            val idSelected = retrievalPlan.memoryItemIds.isEmpty() || item.id in retrievalPlan.memoryItemIds
            layerSelected && idSelected
        }
        val usedLayers = buildList {
            if (retrievalPlan.needShortTerm) {
                add(ChatMemoryLayer.ShortTerm)
            }
            if (selectedItems.any { it.layer == ChatMemoryLayer.WorkingMemory }) {
                add(ChatMemoryLayer.WorkingMemory)
            }
            if (selectedItems.any { it.layer == ChatMemoryLayer.LongTermMemory }) {
                add(ChatMemoryLayer.LongTermMemory)
            }
        }

        val trimmedProfile = userProfile.trim()
        val effectiveSystemPrompt = if (selectedItems.isEmpty() && trimmedProfile.isEmpty()) {
            originalSystemPrompt
        } else {
            buildString {
                append(originalSystemPrompt.trim())
                if (isNotEmpty()) {
                    appendLine()
                    appendLine()
                }
                if (trimmedProfile.isNotEmpty()) {
                    appendLine("[USER_PROFILE]")
                    appendLine(trimmedProfile)
                    if (selectedItems.isNotEmpty()) {
                        appendLine()
                    }
                }
                if (selectedItems.isNotEmpty()) {
                    appendLine("[MEMORY_CONTEXT]")
                }
                selectedItems
                    .groupBy { it.layer }
                    .forEach { (layer, items) ->
                        appendLine(layer.displayTitle)
                        items.sortedByDescending { it.importance }.forEach { item ->
                            appendLine("- [${item.id}] ${item.fact}")
                        }
                    }
            }.trimEnd()
        }

        return MemoryInjection(
            effectiveSystemPrompt = effectiveSystemPrompt,
            usedLayers = usedLayers.distinct(),
            injectedItems = selectedItems,
        )
    }

    fun selectBranch(
        branches: List<ChatBranch>,
        decision: BranchRoutingDecision?,
    ): BranchSelection {
        if (decision is BranchRoutingDecision.Existing && branches.any { it.id == decision.branchId }) {
            return BranchSelection(
                branches = branches,
                activeBranchId = decision.branchId,
            )
        }

        if (decision is BranchRoutingDecision.New) {
            val nextId = (branches.maxOfOrNull { it.id } ?: 0) + 1
            val safeParentId = decision.parentBranchId
                ?.takeIf { parentId -> branches.any { it.id == parentId } }
            val title = decision.title.trim().takeIf { it.isNotEmpty() } ?: "Branch $nextId"
            val summary = decision.summary.trim().takeIf { it.isNotEmpty() } ?: title
            val branch = ChatBranch(
                id = nextId,
                parentId = safeParentId,
                title = title,
                summary = summary,
            )
            return BranchSelection(
                branches = branches + branch,
                activeBranchId = branch.id,
            )
        }

        return fallbackBranch(branches)
    }

    fun fallbackBranch(branches: List<ChatBranch>): BranchSelection {
        val activeBranch = branches.maxByOrNull { it.id }
        if (activeBranch != null) {
            return BranchSelection(
                branches = branches,
                activeBranchId = activeBranch.id,
            )
        }

        val rootBranch = ChatBranch(
            id = 1,
            title = "Main",
            summary = "Основной диалог",
        )
        return BranchSelection(
            branches = listOf(rootBranch),
            activeBranchId = rootBranch.id,
        )
    }

    private fun List<ContextMessage>.shouldCompress(contextManagementSettings: ContextManagementSettings): Boolean {
        val interval = contextManagementSettings.summaryIntervalMessages.coerceAtLeast(1)
        return activeContextMessages().count { it.kind == ChatMessageKind.Regular } >= interval
    }

    private fun List<ContextMessage>.regularMessages(): List<ContextMessage> =
        filter { it.kind == ChatMessageKind.Regular }

    private fun List<ContextMessage>.activeContextMessages(): List<ContextMessage> {
        val lastCompressionIndex = indexOfLast { it.kind == ChatMessageKind.CompressionSummary }
        return if (lastCompressionIndex >= 0) drop(lastCompressionIndex) else this
    }

    private fun List<ContextMessage>.branchPathMessages(
        branches: List<ChatBranch>,
        activeBranchId: Int?,
    ): List<ContextMessage> {
        val branchPathIds = branches.branchPathIds(activeBranchId)
        if (branchPathIds.isEmpty()) {
            return regularMessages()
        }

        return regularMessages().filter { message ->
            message.branchId in branchPathIds
        }
    }

    private fun List<ContextMessage>.lastRegularUserAssistantPair(): List<ContextMessage>? {
        val regularMessages = regularMessages()
        val assistantMessage = regularMessages.lastOrNull()
            ?.takeIf { it.role == ChatRole.Assistant }
            ?: return null
        val userMessage = regularMessages
            .dropLast(1)
            .lastOrNull { it.role == ChatRole.User }
            ?: return null

        return listOf(userMessage, assistantMessage)
    }

    private fun List<StickyFact>.toContextMessages(): List<ContextMessage> {
        if (isEmpty()) {
            return emptyList()
        }

        return listOf(
            ContextMessage(
                role = ChatRole.User,
                content = buildString {
                    appendLine("Sticky facts:")
                    this@toContextMessages
                        .sortedBy { it.key.lowercase() }
                        .forEach { fact ->
                            appendLine("- ${fact.key}: ${fact.value}")
                        }
                }.trimEnd(),
            ),
        )
    }

    private fun branchSummaryUpdateRequest(
        messages: List<ContextMessage>,
        branches: List<ChatBranch>,
        activeBranchId: Int?,
        contextManagementSettings: ContextManagementSettings,
    ): BranchSummaryUpdateRequest? {
        if (contextManagementSettings.mode != ContextManagementMode.Branching || activeBranchId == null) {
            return null
        }

        val branch = branches.firstOrNull { it.id == activeBranchId } ?: return null
        val pair = messages
            .regularMessages()
            .filter { it.branchId == activeBranchId }
            .lastRegularUserAssistantPair()
            ?: return null

        return BranchSummaryUpdateRequest(
            messages = pair,
            prompt = buildString {
                appendLine("Текущая ветка: ${branch.title}")
                appendLine("Текущее summary: ${branch.summary}")
                appendLine()
                append(BranchSummaryUpdatePrompt)
            },
        )
    }

    private fun List<ChatBranch>.branchPathIds(activeBranchId: Int?): Set<Int> {
        if (activeBranchId == null) {
            return emptySet()
        }

        val byId = associateBy { it.id }
        val result = mutableSetOf<Int>()
        var currentId: Int? = activeBranchId
        while (currentId != null && currentId !in result) {
            val branch = byId[currentId] ?: break
            result += branch.id
            currentId = branch.parentId
        }
        return result
    }

    private fun List<ChatBranch>.asTreeRows(): List<BranchTreeRow> {
        val childrenByParent = groupBy { it.parentId }
        fun visit(parentId: Int?, depth: Int): List<BranchTreeRow> =
            childrenByParent[parentId]
                .orEmpty()
                .sortedBy { it.id }
                .flatMap { branch ->
                    listOf(BranchTreeRow(branch, depth)) + visit(branch.id, depth + 1)
                }

        return visit(parentId = null, depth = 0)
    }

    private data class BranchTreeRow(
        val branch: ChatBranch,
        val depth: Int,
    )

    private fun StringBuilder.appendMemoryItems(items: List<ChatMemoryItem>) {
        if (items.isEmpty()) {
            appendLine("- none")
            return
        }

        items.sortedWith(compareBy<ChatMemoryItem> { it.layer.ordinal }.thenBy { it.id })
            .forEach { item ->
                appendLine("- id=${item.id}; layer=${item.layer.storageValue}; importance=${item.importance.coerceIn(0.0, 1.0)}; fact=${item.fact}")
            }
    }
}

private val ChatMemoryLayer.storageValue: String
    get() = when (this) {
        ChatMemoryLayer.ShortTerm -> "short_term"
        ChatMemoryLayer.WorkingMemory -> "working_memory"
        ChatMemoryLayer.LongTermMemory -> "long_term_memory"
    }

private val ChatMemoryLayer.displayTitle: String
    get() = when (this) {
        ChatMemoryLayer.ShortTerm -> "Short-term memory:"
        ChatMemoryLayer.WorkingMemory -> "Working memory:"
        ChatMemoryLayer.LongTermMemory -> "Long-term memory:"
    }
