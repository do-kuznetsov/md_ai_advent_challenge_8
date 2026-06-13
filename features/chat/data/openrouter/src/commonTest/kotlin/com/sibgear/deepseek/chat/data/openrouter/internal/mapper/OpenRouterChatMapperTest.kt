package com.sibgear.deepseek.chat.data.openrouter.internal.mapper

import com.sibgear.deepseek.chat.domain.model.AiModel
import com.sibgear.deepseek.chat.domain.model.AiProvider
import com.sibgear.deepseek.chat.domain.model.AiRequestData
import com.sibgear.deepseek.chat.domain.model.ApiSettings
import com.sibgear.deepseek.chat.domain.model.ChatMessageKind
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessage
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageKind
import com.sibgear.deepseek.chat.history.domain.model.HistoryRole
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
}
