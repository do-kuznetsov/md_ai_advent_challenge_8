package com.sibgear.rag.domain.interactor

import com.sibgear.rag.domain.model.RagQuery
import com.sibgear.rag.domain.model.RagQueryResult
import com.sibgear.rag.domain.repository.EmbeddingProvider
import com.sibgear.rag.domain.repository.RagReranker
import com.sibgear.rag.domain.repository.RagResultProcessor
import com.sibgear.rag.domain.repository.RagSearchRepository

class RagQueryInteractor(
    private val embeddingProvider: EmbeddingProvider,
    private val searchRepository: RagSearchRepository,
    private val resultProcessor: RagResultProcessor = SimilarityThresholdRagResultProcessor(),
    private val reranker: RagReranker? = null,
) {
    suspend fun search(
        query: RagQuery,
        reranker: RagReranker? = this.reranker,
    ): RagQueryResult {
        val config = query.retrievalConfig
        val embedding = embeddingProvider.embed(query.question)
        val rawResults = searchRepository.search(
            indexDirectory = query.indexDirectory,
            strategy = query.strategy,
            queryEmbedding = embedding,
            limit = if (config.isFilteringEnabled || config.isRerankingEnabled) {
                config.topKBeforeFilter
            } else {
                config.topKAfterFilter
            },
        )
        val filteredResults = resultProcessor.process(
            results = rawResults,
            config = config,
        )
        val rerankedResults = if (config.isRerankingEnabled) {
            requireNotNull(reranker) { "RAG reranker is not configured." }
                .rerank(
                    question = query.question,
                    results = filteredResults,
                )
                .sortedWith(
                    compareByDescending<com.sibgear.rag.domain.model.RagSearchResult> {
                        it.rerankScore ?: Float.NEGATIVE_INFINITY
                    }.thenByDescending { it.score },
                )
        } else {
            filteredResults
        }
        val results = rerankedResults.take(config.topKAfterFilter)
        return RagQueryResult(
            results = results,
            rawResultsCount = rawResults.size,
            filteredResultsCount = filteredResults.size,
            rerankedResultsCount = rerankedResults.size,
            rewrittenQuestion = query.rewrittenQuestion,
        )
    }
}
