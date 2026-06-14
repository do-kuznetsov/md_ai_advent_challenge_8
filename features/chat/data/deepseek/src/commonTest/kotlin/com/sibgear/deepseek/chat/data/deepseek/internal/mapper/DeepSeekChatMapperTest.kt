package com.sibgear.deepseek.chat.data.deepseek.internal.mapper

import com.sibgear.deepseek.chat.domain.model.AiModel
import com.sibgear.deepseek.chat.domain.model.AiProvider
import com.sibgear.deepseek.chat.domain.model.AiRequestData
import com.sibgear.deepseek.chat.domain.model.ApiSettings
import com.sibgear.deepseek.chat.domain.model.ChatMessageKind
import com.sibgear.deepseek.chat.domain.model.ContextManagementMode
import com.sibgear.deepseek.chat.domain.model.ContextManagementSettings
import com.sibgear.deepseek.chat.domain.interactor.ChatContextPlanner
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessage
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageKind
import com.sibgear.deepseek.chat.history.domain.model.HistoryRole
import kotlin.test.Test
import kotlin.test.assertEquals

class DeepSeekChatMapperTest {
    @Test
    fun usesApiContentWhenHistoryMessageHasHiddenAttachmentText() {
        val request = AiRequestData(
            systemPrompt = "",
            prompt = "visible prompt",
            model = AiModel(id = "deepseek-v4-flash", provider = AiProvider.DeepSeek),
            apiSettings = ApiSettings(),
        )
        val history = listOf(
            HistoryMessage(
                role = HistoryRole.User,
                content = "visible prompt",
                apiContent = "visible prompt\n\nhidden file text",
            ),
        )

        val apiRequest = request.toDeepSeekChatCompletionRequest(history.toContextMessages())

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
            model = AiModel(id = "deepseek-v4-flash", provider = AiProvider.DeepSeek),
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

        val apiRequest = request.toDeepSeekChatCompletionRequest(plannedContext)

        assertEquals(listOf("kept"), apiRequest.messages.map { it.content })
    }
}
