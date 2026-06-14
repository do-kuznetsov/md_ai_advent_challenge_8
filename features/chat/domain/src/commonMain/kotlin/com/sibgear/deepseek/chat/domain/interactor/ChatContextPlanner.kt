package com.sibgear.deepseek.chat.domain.interactor

import com.sibgear.deepseek.chat.domain.model.BranchRoutingDecision
import com.sibgear.deepseek.chat.domain.model.BranchRoutingRequest
import com.sibgear.deepseek.chat.domain.model.BranchSelection
import com.sibgear.deepseek.chat.domain.model.BranchSummaryUpdatePrompt
import com.sibgear.deepseek.chat.domain.model.BranchSummaryUpdateRequest
import com.sibgear.deepseek.chat.domain.model.ChatBranch
import com.sibgear.deepseek.chat.domain.model.ChatMessageKind
import com.sibgear.deepseek.chat.domain.model.ChatRole
import com.sibgear.deepseek.chat.domain.model.ContextManagementMode
import com.sibgear.deepseek.chat.domain.model.ContextManagementSettings
import com.sibgear.deepseek.chat.domain.model.CompressionRequest
import com.sibgear.deepseek.chat.domain.model.CompressionSummaryPrompt
import com.sibgear.deepseek.chat.domain.model.ContextMessage
import com.sibgear.deepseek.chat.domain.model.ContextPlan
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
}
