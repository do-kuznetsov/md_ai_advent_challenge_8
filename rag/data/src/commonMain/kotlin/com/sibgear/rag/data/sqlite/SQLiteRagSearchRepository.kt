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
        queryText: String,
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
                    val cosineScore = cosineSimilarity(queryEmbedding, embedding)
                    val score = (cosineScore + metadataBoost(queryText, row)).coerceAtMost(MaxScore)
                    row.toSearchResult(score = score)
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

        private const val MaxScore = 1f
        private const val SectionBoostMax = 0.2f
        private const val SectionPhraseBoost = 0.04f
        private const val MetadataBoostPerToken = 0.005f
        private const val MetadataBoostMax = 0.02f
        private const val TotalMetadataBoostMax = 0.26f
        private const val MinimumMetadataTokenLength = 3

        fun metadataBoost(queryText: String, row: RagChunkRow): Float {
            val queryTokenList = queryText.toSearchTokenList()
            val queryTokens = queryTokenList.toSet()
            if (queryTokens.isEmpty()) {
                return 0f
            }

            val sectionTokenList = row.section.toSearchTokenList()
            val sectionTokens = sectionTokenList.toSet()
            val sectionMatches = sectionTokens.count(queryTokens::contains)
            val sectionBoost = if (sectionTokens.isEmpty()) {
                0f
            } else {
                val coverage = sectionMatches.toFloat() / sectionTokens.size
                SectionBoostMax * coverage * coverage
            }
            val phraseBoost = if (sectionTokenList.hasAdjacentPairIn(queryTokenList)) {
                SectionPhraseBoost
            } else {
                0f
            }

            val metadataTokens = "${row.source} ${row.title}".toSearchTokens()
            val metadataMatchCount = queryTokens.count(metadataTokens::contains)
            val metadataBoost = (metadataMatchCount * MetadataBoostPerToken).coerceAtMost(MetadataBoostMax)

            return (sectionBoost + phraseBoost + metadataBoost).coerceAtMost(TotalMetadataBoostMax)
        }

        private fun String.toSearchTokens(): Set<String> =
            toSearchTokenList().toSet()

        private fun String.toSearchTokenList(): List<String> =
            lowercase()
                .split(Regex("[^\\p{L}\\p{Nd}]+"))
                .asSequence()
                .map(String::trim)
                .map { it.normalizeSearchToken() }
                .filter { it.length >= MinimumMetadataTokenLength }
                .toList()

        private fun List<String>.hasAdjacentPairIn(other: List<String>): Boolean {
            if (size < 2 || other.size < 2) {
                return false
            }
            val otherPairs = other.windowed(size = 2).map { it[0] to it[1] }.toSet()
            return windowed(size = 2).any { (left, right) -> left to right in otherPairs }
        }

        private fun String.normalizeSearchToken(): String {
            if (none { it in 'а'..'я' || it == 'ё' }) {
                return this
            }

            val endings = listOf(
                "иями",
                "ями",
                "ами",
                "ого",
                "ему",
                "ыми",
                "ими",
                "ией",
                "иях",
                "ах",
                "ях",
                "ии",
                "ия",
                "ию",
                "ие",
                "ых",
                "их",
                "ой",
                "ый",
                "ий",
                "ая",
                "ую",
                "ом",
                "ем",
                "ам",
                "ям",
                "ов",
                "ев",
                "а",
                "у",
                "ы",
                "и",
                "е",
                "о",
                "я",
            )
            val ending = endings.firstOrNull { endsWith(it) && length - it.length >= MinimumMetadataTokenLength }
            return if (ending == null) this else dropLast(ending.length)
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
