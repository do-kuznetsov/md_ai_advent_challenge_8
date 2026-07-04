package com.sibgear.rag.domain.interactor

import com.sibgear.rag.domain.model.RagQuery
import com.sibgear.rag.domain.model.RagQueryResult
import com.sibgear.rag.domain.repository.EmbeddingProvider
import com.sibgear.rag.domain.repository.RagResultProcessor
import com.sibgear.rag.domain.repository.RagSearchRepository

class RagQueryInteractor(
    private val embeddingProvider: EmbeddingProvider,
    private val searchRepository: RagSearchRepository,
    private val resultProcessor: RagResultProcessor = SimilarityThresholdRagResultProcessor(),
) {
    suspend fun search(query: RagQuery): RagQueryResult {
        val config = query.retrievalConfig
        val embedding = embeddingProvider.embed(query.question)
        val rawResults = searchRepository.search(
            indexDirectory = query.indexDirectory,
            strategy = query.strategy,
            queryEmbedding = embedding,
            limit = if (config.isFilteringEnabled) {
                config.topKBeforeFilter
            } else {
                config.topKAfterFilter
            },
        )
        val results = resultProcessor.process(
            results = rawResults,
            config = config,
        )
        return RagQueryResult(
            results = results,
            rawResultsCount = rawResults.size,
            filteredResultsCount = results.size,
            rewrittenQuestion = query.rewrittenQuestion,
        )
    }
}
