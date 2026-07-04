package com.sibgear.rag.data.sqlite

import com.sibgear.rag.data.sqlite.SQLiteRagIndexRepository.Companion.toFloatArray
import com.sibgear.rag.domain.model.ChunkingStrategyType
import com.sibgear.rag.domain.model.RagSearchResult
import com.sibgear.rag.domain.repository.RagSearchRepository
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlin.math.sqrt

class SQLiteRagSearchRepository : RagSearchRepository {
    override suspend fun search(
        indexDirectory: String,
        strategy: ChunkingStrategyType,
        queryEmbedding: FloatArray,
        limit: Int,
    ): List<RagSearchResult> {
        require(queryEmbedding.isNotEmpty()) { "RAG query embedding is empty." }
        require(limit > 0) { "RAG search limit must be positive." }

        val databaseFile = File(indexDirectory, strategy.databaseFileName)
        require(databaseFile.exists()) {
            "RAG index not found: ${databaseFile.absolutePath}"
        }

        return withConnection(databaseFile) { connection ->
            connection.readChunks(strategy)
                .map { row ->
                    val embedding = row.vectorBlob.toFloatArray()
                    require(embedding.size == queryEmbedding.size) {
                        "RAG embedding dimension mismatch: query=${queryEmbedding.size}, index=${embedding.size}."
                    }
                    row.toSearchResult(score = cosineSimilarity(queryEmbedding, embedding))
                }
                .sortedByDescending(RagSearchResult::score)
                .take(limit)
        }
    }

    private fun Connection.readChunks(strategy: ChunkingStrategyType): List<RagChunkRow> =
        prepareStatement(ReadChunksSql).use { statement ->
            statement.setString(1, strategy.cliName)
            statement.executeQuery().use { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(
                            RagChunkRow(
                                source = resultSet.getString("source"),
                                title = resultSet.getString("title"),
                                section = resultSet.getString("section"),
                                chunkId = resultSet.getString("chunk_id"),
                                text = resultSet.getString("text"),
                                vectorBlob = resultSet.getBytes("vector_blob"),
                            ),
                        )
                    }
                }
            }
        }

    private fun RagChunkRow.toSearchResult(score: Float): RagSearchResult =
        RagSearchResult(
            source = source,
            title = title,
            section = section,
            chunkId = chunkId,
            text = text,
            score = score,
        )

    private fun withConnection(
        databaseFile: File,
        block: (Connection) -> List<RagSearchResult>,
    ): List<RagSearchResult> =
        DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}").use(block)

    private companion object {
        val ChunkingStrategyType.databaseFileName: String
            get() = when (this) {
                ChunkingStrategyType.Fixed -> "rag-fixed.sqlite"
                ChunkingStrategyType.Structure -> "rag-structure.sqlite"
            }

        val ReadChunksSql = """
            SELECT
                d.source,
                d.title,
                c.section,
                c.chunk_id,
                c.text,
                e.vector_blob
            FROM chunks c
            JOIN documents d ON d.id = c.document_id
            JOIN embeddings e ON e.chunk_id = c.id
            WHERE c.strategy = ?
            """.trimIndent()

        fun cosineSimilarity(left: FloatArray, right: FloatArray): Float {
            var dot = 0.0
            var leftNorm = 0.0
            var rightNorm = 0.0
            for (index in left.indices) {
                val leftValue = left[index].toDouble()
                val rightValue = right[index].toDouble()
                dot += leftValue * rightValue
                leftNorm += leftValue * leftValue
                rightNorm += rightValue * rightValue
            }

            if (leftNorm == 0.0 || rightNorm == 0.0) {
                return 0f
            }

            return (dot / (sqrt(leftNorm) * sqrt(rightNorm))).toFloat()
        }
    }
}

private data class RagChunkRow(
    val source: String,
    val title: String,
    val section: String,
    val chunkId: String,
    val text: String,
    val vectorBlob: ByteArray,
)
