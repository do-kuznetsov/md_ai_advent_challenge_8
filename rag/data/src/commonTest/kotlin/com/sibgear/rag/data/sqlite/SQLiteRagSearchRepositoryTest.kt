package com.sibgear.rag.data.sqlite

import com.sibgear.rag.domain.model.ChunkingConfig
import com.sibgear.rag.domain.model.ChunkingStrategyType
import com.sibgear.rag.domain.model.DocumentChunk
import com.sibgear.rag.domain.model.EmbeddedDocumentChunk
import com.sibgear.rag.domain.model.RagIndexRun
import com.sibgear.rag.domain.model.SourceDocument
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SQLiteRagSearchRepositoryTest {
    @Test
    fun searchSortsChunksByCosineSimilarity() = runTest {
        val indexDirectory = Files.createTempDirectory("rag-search").toFile()
        createIndex(
            indexDirectory = indexDirectory,
            strategy = ChunkingStrategyType.Structure,
            chunks = listOf(
                embeddedChunk("first", "alpha", floatArrayOf(1f, 0f), ChunkingStrategyType.Structure),
                embeddedChunk("second", "beta", floatArrayOf(0f, 1f), ChunkingStrategyType.Structure),
                embeddedChunk("third", "gamma", floatArrayOf(0.8f, 0.2f), ChunkingStrategyType.Structure),
            ),
        )

        val results = SQLiteRagSearchRepository().search(
            indexDirectory = indexDirectory.absolutePath,
            strategy = ChunkingStrategyType.Structure,
            queryEmbedding = floatArrayOf(1f, 0f),
        )

        assertEquals(listOf("first", "third", "second"), results.map { it.chunkId })
        assertTrue(results.first().score > results.last().score)
    }

    @Test
    fun searchUsesDatabaseFileForSelectedStrategy() = runTest {
        val indexDirectory = Files.createTempDirectory("rag-search-strategy").toFile()
        createIndex(
            indexDirectory = indexDirectory,
            strategy = ChunkingStrategyType.Fixed,
            chunks = listOf(embeddedChunk("fixed", "fixed text", floatArrayOf(1f, 0f), ChunkingStrategyType.Fixed)),
        )
        createIndex(
            indexDirectory = indexDirectory,
            strategy = ChunkingStrategyType.Structure,
            chunks = listOf(embeddedChunk("structure", "structure text", floatArrayOf(1f, 0f), ChunkingStrategyType.Structure)),
        )

        val fixed = SQLiteRagSearchRepository().search(
            indexDirectory = indexDirectory.absolutePath,
            strategy = ChunkingStrategyType.Fixed,
            queryEmbedding = floatArrayOf(1f, 0f),
        )
        val structure = SQLiteRagSearchRepository().search(
            indexDirectory = indexDirectory.absolutePath,
            strategy = ChunkingStrategyType.Structure,
            queryEmbedding = floatArrayOf(1f, 0f),
        )

        assertEquals("fixed", fixed.single().chunkId)
        assertEquals("structure", structure.single().chunkId)
    }

    @Test
    fun searchFailsWhenDatabaseIsMissing() = runTest {
        val indexDirectory = Files.createTempDirectory("rag-search-missing").toFile()

        val error = assertFailsWith<IllegalArgumentException> {
            SQLiteRagSearchRepository().search(
                indexDirectory = indexDirectory.absolutePath,
                strategy = ChunkingStrategyType.Structure,
                queryEmbedding = floatArrayOf(1f, 0f),
            )
        }

        assertTrue(error.message.orEmpty().contains("RAG index not found"))
    }

    private suspend fun createIndex(
        indexDirectory: java.io.File,
        strategy: ChunkingStrategyType,
        chunks: List<EmbeddedDocumentChunk>,
    ) {
        val databaseFile = indexDirectory.resolve(
            when (strategy) {
                ChunkingStrategyType.Fixed -> "rag-fixed.sqlite"
                ChunkingStrategyType.Structure -> "rag-structure.sqlite"
            },
        )
        SQLiteRagIndexRepository(databaseFile).apply {
            recreate()
            save(
                run = RagIndexRun(
                    inputPath = "/tmp/docs",
                    strategy = strategy,
                    chunkingConfig = ChunkingConfig(500, 50),
                    model = "test",
                ),
                documents = chunks.map {
                    SourceDocument(
                        source = it.chunk.source,
                        title = it.chunk.title,
                        text = it.chunk.text,
                        contentSha256 = it.chunk.chunkId,
                    )
                },
                chunks = chunks,
            )
        }
    }

    private fun embeddedChunk(
        id: String,
        text: String,
        embedding: FloatArray,
        strategy: ChunkingStrategyType,
    ): EmbeddedDocumentChunk =
        EmbeddedDocumentChunk(
            chunk = DocumentChunk(
                source = "$id.md",
                title = "$id.md",
                section = "Section",
                chunkId = id,
                text = text,
                strategy = strategy,
                startToken = 1,
                endToken = 2,
            ),
            embedding = embedding,
        )
}
