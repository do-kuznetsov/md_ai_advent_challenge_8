package com.sibgear.deepseek.chat.domain.interactor

import com.sibgear.deepseek.chat.domain.model.ChatCompressionSettings
import com.sibgear.deepseek.chat.domain.model.ChatMessageKind
import com.sibgear.deepseek.chat.domain.model.CompressionRequest
import com.sibgear.deepseek.chat.domain.model.CompressionSummaryPrompt
import com.sibgear.deepseek.chat.domain.model.ContextMessage
import com.sibgear.deepseek.chat.domain.model.ContextPlan

class ChatContextPlanner {
    fun plan(
        messages: List<ContextMessage>,
        compressionSettings: ChatCompressionSettings,
    ): ContextPlan {
        val apiMessages = if (compressionSettings.isEnabled) {
            messages.activeContextMessages()
        } else {
            messages.filter { it.kind == ChatMessageKind.Regular }
        }

        val compressionRequest = if (compressionSettings.isEnabled && messages.shouldCompress(compressionSettings)) {
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

    private fun List<ContextMessage>.shouldCompress(compressionSettings: ChatCompressionSettings): Boolean {
        val interval = compressionSettings.intervalMessages.coerceAtLeast(1)
        return activeContextMessages().count { it.kind == ChatMessageKind.Regular } >= interval
    }

    private fun List<ContextMessage>.activeContextMessages(): List<ContextMessage> {
        val lastCompressionIndex = indexOfLast { it.kind == ChatMessageKind.CompressionSummary }
        return if (lastCompressionIndex >= 0) drop(lastCompressionIndex) else this
    }
}
