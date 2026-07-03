package com.sibgear.rag.domain.interactor

import com.sibgear.rag.domain.model.RagQuery
import com.sibgear.rag.domain.model.RagSearchResult
import com.sibgear.rag.domain.repository.EmbeddingProvider
import com.sibgear.rag.domain.repository.RagSearchRepository

class RagQueryInteractor(
    private val embeddingProvider: EmbeddingProvider,
    private val searchRepository: RagSearchRepository,
) {
    suspend fun search(query: RagQuery): List<RagSearchResult> {
        val embedding = embeddingProvider.embed(query.question)
        return searchRepository.search(
            indexDirectory = query.indexDirectory,
            strategy = query.strategy,
            queryEmbedding = embedding,
        )
    }
}
