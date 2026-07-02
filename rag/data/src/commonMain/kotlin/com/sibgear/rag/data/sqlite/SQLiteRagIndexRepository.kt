package com.sibgear.rag.data.sqlite

import com.sibgear.rag.domain.model.EmbeddedDocumentChunk
import com.sibgear.rag.domain.model.RagIndexRun
import com.sibgear.rag.domain.model.SourceDocument
import com.sibgear.rag.domain.repository.RagIndexRepository
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.sql.Connection
import java.sql.DriverManager

class SQLiteRagIndexRepository(
    private val databaseFile: File,
) : RagIndexRepository {
    override suspend fun recreate() {
        databaseFile.parentFile?.mkdirs()
        if (databaseFile.exists()) {
            databaseFile.delete()
        }
        withConnection { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(CreateIndexRunsTable)
                statement.executeUpdate(CreateDocumentsTable)
                statement.executeUpdate(CreateChunksTable)
                statement.executeUpdate(CreateEmbeddingsTable)
            }
        }
    }

    override suspend fun save(
        run: RagIndexRun,
        documents: List<SourceDocument>,
        chunks: List<EmbeddedDocumentChunk>,
    ): Long {
        withConnection { connection ->
            connection.autoCommit = false
            try {
                val runId = connection.insertRun(run)
                val documentIds = documents.associate { document ->
                    document.source to connection.insertDocument(runId, document)
                }
                connection.insertChunks(runId, documentIds, chunks)
                connection.commit()
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
        return databaseFile.length()
    }

    private fun Connection.insertRun(run: RagIndexRun): Long {
        prepareStatement(
            """
            INSERT INTO index_runs(input_path, strategy, chunk_size, overlap_size, model, created_at)
            VALUES (?, ?, ?, ?, ?, datetime('now'))
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, run.inputPath)
            statement.setString(2, run.strategy.cliName)
            statement.setLong(3, run.chunkingConfig.chunkSize.toLong())
            statement.setLong(4, run.chunkingConfig.overlapSize.toLong())
            statement.setString(5, run.model)
            statement.executeUpdate()
        }
        return lastInsertId()
    }

    private fun Connection.insertDocument(
        runId: Long,
        document: SourceDocument,
    ): Long {
        prepareStatement(
            """
            INSERT INTO documents(run_id, source, title, content_sha256)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setLong(1, runId)
            statement.setString(2, document.source)
            statement.setString(3, document.title)
            statement.setString(4, document.contentSha256)
            statement.executeUpdate()
        }
        return lastInsertId()
    }

    private fun Connection.insertChunks(
        runId: Long,
        documentIds: Map<String, Long>,
        chunks: List<EmbeddedDocumentChunk>,
    ) {
        val chunkStatement = prepareStatement(
            """
            INSERT INTO chunks(
                run_id,
                document_id,
                strategy,
                chunk_id,
                section,
                start_token,
                end_token,
                text
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        )
        val embeddingStatement = prepareStatement(
            """
            INSERT INTO embeddings(chunk_id, dimension, vector_blob)
            VALUES (?, ?, ?)
            """.trimIndent(),
        )

        chunkStatement.use { chunksStmt ->
            embeddingStatement.use { embeddingsStmt ->
                chunks.forEach { embeddedChunk ->
                    val chunk = embeddedChunk.chunk
                    val documentId = requireNotNull(documentIds[chunk.source]) {
                        "Document id not found for ${chunk.source}."
                    }

                    chunksStmt.setLong(1, runId)
                    chunksStmt.setLong(2, documentId)
                    chunksStmt.setString(3, chunk.strategy.cliName)
                    chunksStmt.setString(4, chunk.chunkId)
                    chunksStmt.setString(5, chunk.section)
                    chunksStmt.setLong(6, chunk.startToken.toLong())
                    chunksStmt.setLong(7, chunk.endToken.toLong())
                    chunksStmt.setString(8, chunk.text)
                    chunksStmt.executeUpdate()

                    val sqliteChunkId = lastInsertId()
                    embeddingsStmt.setLong(1, sqliteChunkId)
                    embeddingsStmt.setLong(2, embeddedChunk.embedding.size.toLong())
                    embeddingsStmt.setBytes(3, embeddedChunk.embedding.toLittleEndianBytes())
                    embeddingsStmt.executeUpdate()
                }
            }
        }
    }

    private fun Connection.lastInsertId(): Long =
        createStatement().use { statement ->
            statement.executeQuery("SELECT last_insert_rowid()").use { resultSet ->
                resultSet.next()
                resultSet.getLong(1)
            }
        }

    private fun <T> withConnection(block: (Connection) -> T): T =
        DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}").use(block)

    companion object {
        fun FloatArray.toLittleEndianBytes(): ByteArray {
            val buffer = ByteBuffer.allocate(size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
            forEach(buffer::putFloat)
            return buffer.array()
        }

        fun ByteArray.toFloatArray(): FloatArray {
            val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
            return FloatArray(size / Float.SIZE_BYTES) { buffer.float }
        }

        private val CreateIndexRunsTable = """
            CREATE TABLE index_runs (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                input_path TEXT NOT NULL,
                strategy TEXT NOT NULL,
                chunk_size INTEGER NOT NULL,
                overlap_size INTEGER NOT NULL,
                model TEXT NOT NULL,
                created_at TEXT NOT NULL
            )
        """.trimIndent()

        private val CreateDocumentsTable = """
            CREATE TABLE documents (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                run_id INTEGER NOT NULL,
                source TEXT NOT NULL,
                title TEXT NOT NULL,
                content_sha256 TEXT NOT NULL,
                FOREIGN KEY(run_id) REFERENCES index_runs(id)
            )
        """.trimIndent()

        private val CreateChunksTable = """
            CREATE TABLE chunks (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                run_id INTEGER NOT NULL,
                document_id INTEGER NOT NULL,
                strategy TEXT NOT NULL,
                chunk_id TEXT NOT NULL,
                section TEXT NOT NULL,
                start_token INTEGER NOT NULL,
                end_token INTEGER NOT NULL,
                text TEXT NOT NULL,
                FOREIGN KEY(run_id) REFERENCES index_runs(id),
                FOREIGN KEY(document_id) REFERENCES documents(id)
            )
        """.trimIndent()

        private val CreateEmbeddingsTable = """
            CREATE TABLE embeddings (
                chunk_id INTEGER NOT NULL PRIMARY KEY,
                dimension INTEGER NOT NULL,
                vector_blob BLOB NOT NULL,
                FOREIGN KEY(chunk_id) REFERENCES chunks(id)
            )
        """.trimIndent()
    }
}
