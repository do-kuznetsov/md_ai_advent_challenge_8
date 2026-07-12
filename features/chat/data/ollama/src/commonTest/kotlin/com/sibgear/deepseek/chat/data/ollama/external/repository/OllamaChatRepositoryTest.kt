package com.sibgear.deepseek.chat.data.ollama.external.repository

import kotlin.test.Test
import kotlin.test.assertEquals

class OllamaChatRepositoryTest {
    @Test
    fun decodesStreamingJsonLinesResponse() {
        val body = """
            {"model":"qwen3:8b","message":{"role":"assistant","thinking":"Сначала думаю."},"done":false}
            {"model":"qwen3:8b","message":{"role":"assistant","content":"Привет"},"done":false}
            {"model":"qwen3:8b","message":{"role":"assistant","thinking":" Потом отвечаю."},"done":false}
            {"model":"qwen3:8b","message":{"role":"assistant","content":", Дима!"},"done":false}
            {"model":"qwen3:8b","done":true,"prompt_eval_count":12,"eval_count":7}
        """.trimIndent()

        val response = body.decodeOllamaChatResponse(ollamaJson())

        assertEquals("assistant", response.message?.role)
        assertEquals("Сначала думаю. Потом отвечаю.", response.message?.thinking)
        assertEquals("Привет, Дима!", response.message?.content)
        assertEquals(true, response.done)
        assertEquals(12, response.promptEvalCount)
        assertEquals(7, response.evalCount)
    }

    @Test
    fun decodesSingleJsonResponse() {
        val body = """
            {"model":"qwen3:8b","message":{"role":"assistant","thinking":"Думаю.","content":"Привет!"},"done":true}
        """.trimIndent()

        val response = body.decodeOllamaChatResponse(ollamaJson())

        assertEquals("Думаю.", response.message?.thinking)
        assertEquals("Привет!", response.message?.content)
        assertEquals(true, response.done)
    }
}
