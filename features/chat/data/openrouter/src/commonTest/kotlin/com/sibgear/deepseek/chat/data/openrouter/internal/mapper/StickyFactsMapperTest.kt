package com.sibgear.deepseek.chat.data.openrouter.internal.mapper

import com.sibgear.deepseek.chat.domain.model.StickyFact
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class StickyFactsMapperTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun mergesFencedJsonPatch() {
        val facts = """
            ```json
            {
              "goal": "build app"
            }
            ```
        """.trimIndent().mergeStickyFacts(
            currentFacts = listOf(StickyFact(key = "preference", value = "short")),
            json = json,
        )

        assertEquals(
            listOf(
                StickyFact(key = "goal", value = "build app"),
                StickyFact(key = "preference", value = "short"),
            ),
            facts,
        )
    }
}
