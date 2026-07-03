package com.sibgear.rag.domain.repository

import com.sibgear.rag.domain.model.EmbeddedDocumentChunk
import com.sibgear.rag.domain.model.RagIndexRun
import com.sibgear.rag.domain.model.RagSearchResult
import com.sibgear.rag.domain.model.ChunkingStrategyType
import com.sibgear.rag.domain.model.SourceDocument

interface DocumentScanner {
    suspend fun scan(inputPath: String): com.sibgear.rag.domain.model.DocumentScanResult
}

interface EmbeddingProvider {
    suspend fun embed(text: String): FloatArray
}

interface RagIndexRepository {
    suspend fun recreate()

    suspend fun save(
        run: RagIndexRun,
        documents: List<SourceDocument>,
        chunks: List<EmbeddedDocumentChunk>,
    ): Long
}

interface RagSearchRepository {
    suspend fun search(
        indexDirectory: String,
        strategy: ChunkingStrategyType,
        queryEmbedding: FloatArray,
    ): List<RagSearchResult>
}
