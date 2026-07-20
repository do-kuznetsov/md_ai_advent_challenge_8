package com.sibgear.deepseek.chat.data.deepseek.internal.repository

import com.sibgear.deepseek.chat.domain.model.StreamingChatDelta
import com.sibgear.deepseek.chat.domain.model.StreamingChatDeltaType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DeepSeekStreamAccumulatorTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun collectsReasoningContentAndContentDeltas() = runBlocking {
        val deltas = mutableListOf<StreamingChatDelta>()
        val accumulator = DeepSeekStreamAccumulator(json) { delta ->
            deltas += delta
        }

        accumulator.acceptSseData(
            """
            {"choices":[{"delta":{"reasoning_content":"Думаю "}}]}
            """.trimIndent(),
        )
        accumulator.acceptSseData(
            """
            {"choices":[{"delta":{"content":"Ответ"}}],"usage":{"prompt_tokens":3,"completion_tokens":5}}
            """.trimIndent(),
        )

        val result = accumulator.result()

        assertEquals("Думаю ", result.reasoningContent)
        assertEquals("Ответ", result.content)
        assertEquals(3, result.usage?.promptTokens)
        assertEquals(5, result.usage?.completionTokens)
        assertFalse(result.hasStreamError)
        assertEquals(
            listOf(
                StreamingChatDelta(StreamingChatDeltaType.Thinking, "Думаю "),
                StreamingChatDelta(StreamingChatDeltaType.Content, "Ответ"),
            ),
            deltas,
        )
    }

    @Test
    fun collectsFragmentedToolCallsByIndex() = runBlocking {
        val accumulator = DeepSeekStreamAccumulator(json) {}

        accumulator.acceptSseData(
            """
            {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"read_project_file","arguments":"{\"relative_"}}]}}]}
            """.trimIndent(),
        )
        accumulator.acceptSseData(
            """
            {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"path\":\"README.md\"}"}}]}}]}
            """.trimIndent(),
        )

        val toolCall = accumulator.result().toolCalls.single()

        assertEquals("call_1", toolCall.id)
        assertEquals("function", toolCall.type)
        assertEquals("read_project_file", toolCall.function.name)
        assertEquals("""{"relative_path":"README.md"}""", toolCall.function.arguments)
    }

    private suspend fun DeepSeekStreamAccumulator.acceptSseData(data: String) {
        acceptLine("data: $data")
        acceptLine("")
    }
}
