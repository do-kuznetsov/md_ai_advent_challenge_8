package com.sibgear.server

import com.sibgear.server.protocol.ChatStreamEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LlamaStreamParserTest {
    @Test
    fun parsesReasoningContentAsThinkingDelta() {
        val parser = LlamaStreamParser()
        val events = mutableListOf<ChatStreamEvent>()

        parser.acceptSseLine(
            """data: {"choices":[{"delta":{"reasoning_content":"think ","content":"answer"}}]}""",
            events::add,
        )

        assertEquals("think ", assertIs<ChatStreamEvent.ThinkingDelta>(events[0]).text)
        assertEquals("answer", assertIs<ChatStreamEvent.ContentDelta>(events[1]).text)
        assertEquals("think ", parser.result().thinking)
        assertEquals("answer", parser.result().content)
    }

    @Test
    fun splitsQwenThinkTagsAcrossChunks() {
        val parser = LlamaStreamParser()
        val events = mutableListOf<ChatStreamEvent>()

        parser.acceptSseLine("""data: {"choices":[{"delta":{"content":"<thi"}}]}""", events::add)
        parser.acceptSseLine("""data: {"choices":[{"delta":{"content":"nk>plan</thi"}}]}""", events::add)
        parser.acceptSseLine("""data: {"choices":[{"delta":{"content":"nk>done"}}]}""", events::add)
        parser.acceptSseLine("data: [DONE]", events::add)

        assertEquals(
            listOf(
                ChatStreamEvent.ThinkingDelta("plan"),
                ChatStreamEvent.ContentDelta("done"),
            ),
            events,
        )
        assertEquals("plan", parser.result().thinking)
        assertEquals("done", parser.result().content)
    }

    @Test
    fun keepsStreamErrorInFinalContent() {
        val parser = LlamaStreamParser()
        val events = mutableListOf<ChatStreamEvent>()

        parser.acceptSseLine("""data: {"error":{"message":"boom"}}""", events::add)

        assertEquals("llama.cpp stream error: boom", parser.result().content)
        assertEquals(true, parser.result().isError)
    }
}
