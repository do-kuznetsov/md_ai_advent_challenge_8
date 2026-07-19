package com.sibgear.aireview

import com.sibgear.rag.data.embedding.OnnxNomicEmbeddingProvider
import com.sibgear.rag.data.sqlite.SQLiteRagSearchRepository
import com.sibgear.rag.domain.interactor.RagQueryInteractor
import com.sibgear.rag.domain.model.ChunkingStrategyType
import com.sibgear.rag.domain.model.RagQuery
import com.sibgear.rag.domain.model.RagRetrievalConfig

interface ReviewRagProvider {
    suspend fun findContext(query: String): List<ReviewRagChunk>
}

class OnnxReviewRagProvider(
    private val config: AiReviewConfig,
    private val interactor: RagQueryInteractor = RagQueryInteractor(
        embeddingProvider = OnnxNomicEmbeddingProvider(config.embeddingModelDirectory),
        searchRepository = SQLiteRagSearchRepository(),
    ),
) : ReviewRagProvider {
    override suspend fun findContext(query: String): List<ReviewRagChunk> =
        interactor.search(
            RagQuery(
                strategy = ChunkingStrategyType.Structure,
                indexDirectory = config.ragIndexDirectory,
                question = query,
                retrievalConfig = RagRetrievalConfig(
                    topKBeforeFilter = 15,
                    topKAfterFilter = 5,
                    similarityThreshold = 0.7f,
                    isFilteringEnabled = true,
                    isRerankingEnabled = false,
                ),
            ),
        ).results.map { result ->
            ReviewRagChunk(
                source = result.source,
                section = result.section,
                chunkId = result.chunkId,
                text = result.text,
            )
        }
}
