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
            queryText = "",
            queryEmbedding = floatArrayOf(1f, 0f),
            limit = 5,
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
            queryText = "",
            queryEmbedding = floatArrayOf(1f, 0f),
            limit = 5,
        )
        val structure = SQLiteRagSearchRepository().search(
            indexDirectory = indexDirectory.absolutePath,
            strategy = ChunkingStrategyType.Structure,
            queryText = "",
            queryEmbedding = floatArrayOf(1f, 0f),
            limit = 5,
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
                queryText = "",
                queryEmbedding = floatArrayOf(1f, 0f),
                limit = 5,
            )
        }

        assertTrue(error.message.orEmpty().contains("RAG index not found"))
    }

    @Test
    fun searchAppliesDynamicLimit() = runTest {
        val indexDirectory = Files.createTempDirectory("rag-search-limit").toFile()
        createIndex(
            indexDirectory = indexDirectory,
            strategy = ChunkingStrategyType.Structure,
            chunks = listOf(
                embeddedChunk("first", "alpha", floatArrayOf(1f, 0f), ChunkingStrategyType.Structure),
                embeddedChunk("second", "beta", floatArrayOf(0.9f, 0.1f), ChunkingStrategyType.Structure),
                embeddedChunk("third", "gamma", floatArrayOf(0.8f, 0.2f), ChunkingStrategyType.Structure),
            ),
        )

        val results = SQLiteRagSearchRepository().search(
            indexDirectory = indexDirectory.absolutePath,
            strategy = ChunkingStrategyType.Structure,
            queryText = "",
            queryEmbedding = floatArrayOf(1f, 0f),
            limit = 2,
        )

        assertEquals(listOf("first", "second"), results.map { it.chunkId })
    }

    @Test
    fun searchAppliesSmallMetadataBoost() = runTest {
        val indexDirectory = Files.createTempDirectory("rag-search-boost").toFile()
        createIndex(
            indexDirectory = indexDirectory,
            strategy = ChunkingStrategyType.Structure,
            chunks = listOf(
                embeddedChunk(
                    id = "best-vector",
                    text = "generic module text",
                    embedding = floatArrayOf(0.99f, 0.1f),
                    strategy = ChunkingStrategyType.Structure,
                    section = "Generic",
                ),
                embeddedChunk(
                    id = "matching-section",
                    text = "compile troubleshooting text",
                    embedding = floatArrayOf(0.98f, 0.1f),
                    strategy = ChunkingStrategyType.Structure,
                    section = "Типичные ошибки компиляции и способы их устранения",
                ),
            ),
        )

        val results = SQLiteRagSearchRepository().search(
            indexDirectory = indexDirectory.absolutePath,
            strategy = ChunkingStrategyType.Structure,
            queryText = "Какие типичные ошибки компиляции описаны?",
            queryEmbedding = floatArrayOf(1f, 0f),
            limit = 2,
        )

        assertEquals(listOf("matching-section", "best-vector"), results.map { it.chunkId })
        assertTrue(results.first().score > results.last().score)
    }

    @Test
    fun searchMatchesRussianSectionFormsInMetadataBoost() = runTest {
        val indexDirectory = Files.createTempDirectory("rag-search-russian-boost").toFile()
        createIndex(
            indexDirectory = indexDirectory,
            strategy = ChunkingStrategyType.Structure,
            chunks = listOf(
                embeddedChunk(
                    id = "generic",
                    text = "migration checklist",
                    embedding = floatArrayOf(0.99f, 0.1f),
                    strategy = ChunkingStrategyType.Structure,
                    section = "Checklist готовности Android модуля к портированию на KMP",
                ),
                embeddedChunk(
                    id = "configure-phase",
                    text = "configuration steps",
                    embedding = floatArrayOf(0.94f, 0.1f),
                    strategy = ChunkingStrategyType.Structure,
                    section = "Фаза конфигурации",
                ),
            ),
        )

        val results = SQLiteRagSearchRepository().search(
            indexDirectory = indexDirectory.absolutePath,
            strategy = ChunkingStrategyType.Structure,
            queryText = "Какие шаги входят в фазу конфигурации миграции?",
            queryEmbedding = floatArrayOf(1f, 0f),
            limit = 2,
        )

        assertEquals("configure-phase", results.first().chunkId)
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
        section: String = "Section",
    ): EmbeddedDocumentChunk =
        EmbeddedDocumentChunk(
            chunk = DocumentChunk(
                source = "$id.md",
                title = "$id.md",
                section = section,
                chunkId = id,
                text = text,
                strategy = strategy,
                startToken = 1,
                endToken = 2,
            ),
            embedding = embedding,
        )
}
