package com.sibgear.deepseek.chat.data.ollama.internal.mapper

import com.sibgear.deepseek.chat.domain.model.AiModel
import com.sibgear.deepseek.chat.domain.model.AiProvider
import com.sibgear.deepseek.chat.domain.model.AiRequestData
import com.sibgear.deepseek.chat.domain.model.ApiSettings
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessage
import com.sibgear.deepseek.chat.history.domain.model.HistoryRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OllamaChatMapperTest {
    @Test
    fun requestContainsSelectedModelSystemHistoryUserAndStreamFalse() {
        val request = request(apiSettings = ApiSettings())
        val apiRequest = request.toOllamaChatRequest(
            contextMessages = listOf(HistoryMessage(role = HistoryRole.User, content = "hello"))
                .toContextMessages(),
            effectiveSystemPrompt = "system",
        )

        assertEquals("qwen3:8b", apiRequest.model)
        assertEquals(false, apiRequest.stream)
        assertEquals(listOf("system", "user"), apiRequest.messages.map { it.role })
        assertEquals(listOf("system", "hello"), apiRequest.messages.map { it.content })
        assertNull(apiRequest.options)
    }

    @Test
    fun requestContainsOptionsOnlyWhenApiControlIsEnabled() {
        val request = request(
            apiSettings = ApiSettings(
                temperature = 0.7f,
                maxTokens = 128,
                stopWord = "STOP",
                isApiControlEnabled = true,
            ),
        )

        val apiRequest = request.toOllamaChatRequest(contextMessages = emptyList())

        assertEquals(0.7f, apiRequest.options?.temperature)
        assertEquals(128, apiRequest.options?.numPredict)
        assertEquals(listOf("STOP"), apiRequest.options?.stop)
    }

    private fun request(apiSettings: ApiSettings): AiRequestData =
        AiRequestData(
            systemPrompt = "",
            prompt = "hello",
            model = AiModel(id = "qwen3:8b", provider = AiProvider.Ollama),
            apiSettings = apiSettings,
        )
}
