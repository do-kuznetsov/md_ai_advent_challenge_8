package com.sibgear.deepseek.chat.data.deepseek.internal.mapper

import com.sibgear.deepseek.chat.domain.model.StickyFact
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class StickyFactsMapperTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun mergesJsonPatchAndRemovesNullValues() {
        val facts = """
            {
              "goal": "build app",
              "obsolete": null
            }
        """.trimIndent().mergeStickyFacts(
            currentFacts = listOf(
                StickyFact(key = "obsolete", value = "remove me"),
                StickyFact(key = "preference", value = "short"),
            ),
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
