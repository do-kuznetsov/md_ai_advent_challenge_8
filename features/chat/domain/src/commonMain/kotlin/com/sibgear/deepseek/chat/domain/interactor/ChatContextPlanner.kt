package com.sibgear.deepseek.chat.domain.interactor

import com.sibgear.deepseek.chat.domain.model.ChatMessageKind
import com.sibgear.deepseek.chat.domain.model.ContextManagementMode
import com.sibgear.deepseek.chat.domain.model.ContextManagementSettings
import com.sibgear.deepseek.chat.domain.model.CompressionRequest
import com.sibgear.deepseek.chat.domain.model.CompressionSummaryPrompt
import com.sibgear.deepseek.chat.domain.model.ContextMessage
import com.sibgear.deepseek.chat.domain.model.ContextPlan

class ChatContextPlanner {
    fun plan(
        messages: List<ContextMessage>,
        contextManagementSettings: ContextManagementSettings,
    ): ContextPlan {
        val apiMessages = when (contextManagementSettings.mode) {
            ContextManagementMode.None -> messages.regularMessages()
            ContextManagementMode.ContextSummary -> messages.activeContextMessages()
            ContextManagementMode.SlidingWindow -> messages.regularMessages()
                .takeLast(contextManagementSettings.slidingWindowMessages.coerceAtLeast(1))
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

        return ContextPlan(
            apiMessages = apiMessages,
            compressionRequest = compressionRequest,
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
}
