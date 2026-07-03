package com.sibgear.rag.domain.model

enum class ChunkingStrategyType(val cliName: String) {
    Fixed("fixed"),
    Structure("structure"),
}

data class ChunkingConfig(
    val chunkSize: Int,
    val overlapSize: Int,
) {
    init {
        require(chunkSize > 0) { "chunkSize must be positive." }
        require(overlapSize >= 0) { "overlapSize must be zero or positive." }
        require(overlapSize < chunkSize) { "overlapSize must be less than chunkSize." }
    }
}

data class SourceDocument(
    val source: String,
    val title: String,
    val text: String,
    val contentSha256: String,
)

data class DocumentScanResult(
    val documents: List<SourceDocument>,
    val warnings: List<String>,
)

data class DocumentChunk(
    val source: String,
    val title: String,
    val section: String,
    val chunkId: String,
    val text: String,
    val strategy: ChunkingStrategyType,
    val startToken: Int,
    val endToken: Int,
)

data class EmbeddedDocumentChunk(
    val chunk: DocumentChunk,
    val embedding: FloatArray,
) {
    override fun equals(other: Any?): Boolean =
        other is EmbeddedDocumentChunk &&
            chunk == other.chunk &&
            embedding.contentEquals(other.embedding)

    override fun hashCode(): Int =
        31 * chunk.hashCode() + embedding.contentHashCode()
}

data class RagIndexRun(
    val inputPath: String,
    val strategy: ChunkingStrategyType,
    val chunkingConfig: ChunkingConfig,
    val model: String,
)

data class RagIndexSummary(
    val strategy: ChunkingStrategyType,
    val files: Int,
    val chunks: Int,
    val embeddingDimension: Int,
    val elapsedMs: Long,
    val databaseSizeBytes: Long,
    val warnings: List<String>,
)

data class RagSearchResult(
    val source: String,
    val title: String,
    val section: String,
    val chunkId: String,
    val text: String,
    val score: Float,
)

data class RagQuery(
    val strategy: ChunkingStrategyType,
    val indexDirectory: String,
    val question: String,
)
