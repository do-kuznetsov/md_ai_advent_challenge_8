package com.sibgear.deepseek.data.openrouter

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenRouterStreamParserTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun collectsContentAndUsageFromStreamChunks() {
        val accumulator = OpenRouterStreamAccumulator(json)

        listOf(
            """data: {"choices":[{"delta":{"content":"Hello"}}]}""",
            "",
            """data: {"choices":[{"delta":{"content":" world"}}]}""",
            "",
            """data: {"usage":{"prompt_tokens":7,"completion_tokens":2,"total_tokens":9,"cost":0}}""",
            "",
            "data: [DONE]",
            "",
        ).forEach(accumulator::acceptLine)

        val result = accumulator.result()

        assertEquals("Hello world", result.content)
        assertEquals(7, result.usage?.promptTokens)
        assertEquals(2, result.usage?.completionTokens)
        assertEquals(9, result.usage?.totalTokens)
        assertEquals(0.0, result.usage?.cost)
    }

    @Test
    fun exposesMidStreamErrorAsResultText() {
        val accumulator = OpenRouterStreamAccumulator(json)

        listOf(
            """data: {"choices":[{"delta":{"content":"Partial"}}]}""",
            "",
            """data: {"error":{"message":"Provider disconnected unexpectedly"},"choices":[{"finish_reason":"error"}]}""",
            "",
        ).forEach(accumulator::acceptLine)

        val result = accumulator.result()

        assertTrue(result.hasStreamError)
        assertEquals(
            "Partial\n\nОшибка OpenRouter stream: Provider disconnected unexpectedly",
            result.content,
        )
    }
}
