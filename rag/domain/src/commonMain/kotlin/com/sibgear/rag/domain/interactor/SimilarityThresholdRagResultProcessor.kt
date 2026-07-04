package com.sibgear.rag.domain.interactor

import com.sibgear.rag.domain.model.RagRetrievalConfig
import com.sibgear.rag.domain.model.RagSearchResult
import com.sibgear.rag.domain.repository.RagResultProcessor

class SimilarityThresholdRagResultProcessor : RagResultProcessor {
    override fun process(
        results: List<RagSearchResult>,
        config: RagRetrievalConfig,
    ): List<RagSearchResult> {
        val filtered = if (config.isFilteringEnabled) {
            results.filter { it.score >= config.similarityThreshold }
        } else {
            results
        }
        return filtered.take(config.topKAfterFilter)
    }
}
