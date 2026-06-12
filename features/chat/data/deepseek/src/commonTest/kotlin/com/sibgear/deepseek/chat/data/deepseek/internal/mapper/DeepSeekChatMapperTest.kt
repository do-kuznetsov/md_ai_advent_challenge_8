package com.sibgear.deepseek.chat.data.deepseek.internal.mapper

import com.sibgear.deepseek.chat.domain.model.AiModel
import com.sibgear.deepseek.chat.domain.model.AiProvider
import com.sibgear.deepseek.chat.domain.model.AiRequestData
import com.sibgear.deepseek.chat.domain.model.ApiSettings
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessage
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

        val apiRequest = request.toDeepSeekChatCompletionRequest(history)

        assertEquals("visible prompt\n\nhidden file text", apiRequest.messages.single().content)
    }
}
