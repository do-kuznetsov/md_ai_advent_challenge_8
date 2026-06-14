package com.sibgear.deepseek.chat.domain.interactor

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
    ): ContextPlan {
        val apiMessages = when (contextManagementSettings.mode) {
            ContextManagementMode.None -> messages.regularMessages()
            ContextManagementMode.ContextSummary -> messages.activeContextMessages()
            ContextManagementMode.SlidingWindow -> messages.regularMessages()
                .takeLast(contextManagementSettings.slidingWindowMessages.coerceAtLeast(1))
            ContextManagementMode.StickyFacts -> stickyFacts.toContextMessages() + messages.regularMessages()
                .takeLast(contextManagementSettings.stickyFactsWindowMessages.coerceAtLeast(1))
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
}
