package com.sibgear.rag.data.sqlite

import com.sibgear.rag.data.sqlite.SQLiteRagIndexRepository.Companion.toFloatArray
import com.sibgear.rag.data.sqlite.SQLiteRagIndexRepository.Companion.toLittleEndianBytes
import com.sibgear.rag.domain.model.ChunkingConfig
import com.sibgear.rag.domain.model.ChunkingStrategyType
import com.sibgear.rag.domain.model.DocumentChunk
import com.sibgear.rag.domain.model.EmbeddedDocumentChunk
import com.sibgear.rag.domain.model.RagIndexRun
import com.sibgear.rag.domain.model.SourceDocument
import java.nio.file.Files
import java.sql.DriverManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SQLiteRagIndexRepositoryTest {
    @Test
    fun storesRunDocumentChunkAndEmbedding() = runTest {
        val file = Files.createTempDirectory("rag-index").resolve("rag-fixed.sqlite").toFile()
        val repository = SQLiteRagIndexRepository(file)
        val document = SourceDocument("README.md", "README.md", "hello world", "sha")
        val chunk = DocumentChunk(
            source = "README.md",
            title = "README.md",
            section = "README.md",
            chunkId = "README.md#fixed-1",
            text = "hello world",
            strategy = ChunkingStrategyType.Fixed,
            startToken = 1,
            endToken = 2,
        )

        repository.recreate()
        val size = repository.save(
            run = RagIndexRun(
                inputPath = "/tmp/docs",
                strategy = ChunkingStrategyType.Fixed,
                chunkingConfig = ChunkingConfig(500, 50),
                model = "test",
            ),
            documents = listOf(document),
            chunks = listOf(EmbeddedDocumentChunk(chunk, floatArrayOf(1f, 2f))),
        )

        assertTrue(size > 0)
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
            assertEquals(1, connection.count("index_runs"))
            assertEquals(1, connection.count("documents"))
            assertEquals(1, connection.count("chunks"))
            assertEquals(1, connection.count("embeddings"))
        }
    }

    @Test
    fun embeddingBlobRoundTripsFloatArray() {
        val original = floatArrayOf(1.25f, -2f, 3.5f)

        val restored = original.toLittleEndianBytes().toFloatArray()

        assertContentEquals(original, restored)
    }

    private fun java.sql.Connection.count(table: String): Int =
        createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM $table").use { resultSet ->
                resultSet.next()
                resultSet.getInt(1)
            }
        }
}
