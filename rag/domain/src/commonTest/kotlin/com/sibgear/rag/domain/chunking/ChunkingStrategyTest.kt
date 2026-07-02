package com.sibgear.rag.domain.chunking

import com.sibgear.rag.domain.model.ChunkingConfig
import com.sibgear.rag.domain.model.ChunkingStrategyType
import com.sibgear.rag.domain.model.SourceDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChunkingStrategyTest {
    @Test
    fun fixedChunkingKeepsOverlap() {
        val chunks = FixedWindowChunkingStrategy().chunk(
            document = document("one two three four five six seven"),
            config = ChunkingConfig(chunkSize = 3, overlapSize = 1),
        )

        assertEquals(listOf("one two three", "three four five", "five six seven"), chunks.map { it.text })
        assertEquals(3, chunks.size)
        assertEquals(ChunkingStrategyType.Fixed, chunks.first().strategy)
    }

    @Test
    fun fixedChunkingSupportsZeroOverlap() {
        val chunks = FixedWindowChunkingStrategy().chunk(
            document = document("one two three four"),
            config = ChunkingConfig(chunkSize = 2, overlapSize = 0),
        )

        assertEquals(listOf("one two", "three four"), chunks.map { it.text })
    }

    @Test
    fun structureChunkingUsesMarkdownHeadings() {
        val chunks = StructureChunkingStrategy().chunk(
            document = document(
                """
                # Intro
                alpha beta
                ## Details
                gamma delta
                """.trimIndent(),
            ),
            config = ChunkingConfig(chunkSize = 10, overlapSize = 0),
        )

        assertEquals(listOf("Intro", "Details"), chunks.map { it.section })
        assertEquals(listOf("alpha beta", "gamma delta"), chunks.map { it.text })
    }

    @Test
    fun structureChunkingSplitsLargeSectionByWindow() {
        val chunks = StructureChunkingStrategy().chunk(
            document = document(
                """
                # Intro
                one two three four five
                """.trimIndent(),
            ),
            config = ChunkingConfig(chunkSize = 2, overlapSize = 1),
        )

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.section == "Intro" })
    }

    private fun document(text: String): SourceDocument =
        SourceDocument(
            source = "doc.md",
            title = "doc.md",
            text = text,
            contentSha256 = "sha",
        )
}
