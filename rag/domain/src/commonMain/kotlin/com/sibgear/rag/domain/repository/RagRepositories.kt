package com.sibgear.rag.domain.repository

import com.sibgear.rag.domain.model.EmbeddedDocumentChunk
import com.sibgear.rag.domain.model.RagIndexRun
import com.sibgear.rag.domain.model.RagRetrievalConfig
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
        queryText: String,
        queryEmbedding: FloatArray,
        limit: Int,
    ): List<RagSearchResult>
}

fun interface RagReranker {
    suspend fun rerank(
        question: String,
        results: List<RagSearchResult>,
    ): List<RagSearchResult>
}

fun interface RagResultProcessor {
    fun process(
        results: List<RagSearchResult>,
        config: RagRetrievalConfig,
    ): List<RagSearchResult>
}
