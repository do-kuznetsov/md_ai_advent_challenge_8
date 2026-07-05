package com.sibgear.rag.domain.interactor

import com.sibgear.rag.domain.model.ChunkingStrategyType
import com.sibgear.rag.domain.model.RagQuery
import com.sibgear.rag.domain.model.RagRetrievalConfig
import com.sibgear.rag.domain.model.RagSearchResult
import com.sibgear.rag.domain.repository.EmbeddingProvider
import com.sibgear.rag.domain.repository.RagReranker
import com.sibgear.rag.domain.repository.RagResultProcessor
import com.sibgear.rag.domain.repository.RagSearchRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RagQueryInteractorTest {
    @Test
    fun filterKeepsOnlyResultsAboveThreshold() = runTest {
        val repository = RecordingRagSearchRepository(
            results = listOf(
                result("first", 0.9f),
                result("second", 0.69f),
                result("third", 0.7f),
            ),
        )
        val interactor = RagQueryInteractor(
            embeddingProvider = RecordingEmbeddingProvider(),
            searchRepository = repository,
        )

        val response = interactor.search(
            query(
                RagRetrievalConfig(
                    topKBeforeFilter = 15,
                    topKAfterFilter = 5,
                    similarityThreshold = 0.7f,
                    isFilteringEnabled = true,
                ),
            ),
        )

        assertEquals(listOf("first", "third"), response.results.map { it.chunkId })
        assertEquals(3, response.rawResultsCount)
        assertEquals(2, response.filteredResultsCount)
    }

    @Test
    fun filterPassesTopKBeforeFilterToSearch() = runTest {
        val repository = RecordingRagSearchRepository()
        val interactor = RagQueryInteractor(
            embeddingProvider = RecordingEmbeddingProvider(),
            searchRepository = repository,
        )

        interactor.search(
            query(
                RagRetrievalConfig(
                    topKBeforeFilter = 15,
                    topKAfterFilter = 5,
                    isFilteringEnabled = true,
                ),
            ),
        )

        assertEquals(15, repository.lastLimit)
    }

    @Test
    fun filterDisabledUsesTopKAfterFilterAsSearchLimit() = runTest {
        val repository = RecordingRagSearchRepository(
            results = listOf(
                result("first", 0.9f),
                result("second", 0.8f),
                result("third", 0.7f),
                result("fourth", 0.6f),
                result("fifth", 0.5f),
            ),
        )
        val interactor = RagQueryInteractor(
            embeddingProvider = RecordingEmbeddingProvider(),
            searchRepository = repository,
        )

        val response = interactor.search(
            query(RagRetrievalConfig(isFilteringEnabled = false)),
        )

        assertEquals(5, repository.lastLimit)
        assertEquals(listOf("first", "second", "third", "fourth", "fifth"), response.results.map { it.chunkId })
    }

    @Test
    fun rerankPassesTopKBeforeFilterToSearch() = runTest {
        val repository = RecordingRagSearchRepository()
        val interactor = RagQueryInteractor(
            embeddingProvider = RecordingEmbeddingProvider(),
            searchRepository = repository,
            reranker = RecordingReranker(),
        )

        interactor.search(
            query(
                RagRetrievalConfig(
                    topKBeforeFilter = 15,
                    topKAfterFilter = 5,
                    isRerankingEnabled = true,
                ),
            ),
        )

        assertEquals(15, repository.lastLimit)
    }

    @Test
    fun rerankerReceivesFilteredResults() = runTest {
        val reranker = RecordingReranker()
        val interactor = RagQueryInteractor(
            embeddingProvider = RecordingEmbeddingProvider(),
            searchRepository = RecordingRagSearchRepository(
                results = listOf(
                    result("high", 0.8f),
                    result("low", 0.6f),
                ),
            ),
            reranker = reranker,
        )

        interactor.search(
            query(
                RagRetrievalConfig(
                    isFilteringEnabled = true,
                    isRerankingEnabled = true,
                    similarityThreshold = 0.7f,
                ),
            ),
        )

        assertEquals(listOf("high"), reranker.lastResults.map { it.chunkId })
    }

    @Test
    fun rerankSortsByRerankScoreAndAppliesTopKAfterFilter() = runTest {
        val interactor = RagQueryInteractor(
            embeddingProvider = RecordingEmbeddingProvider(),
            searchRepository = RecordingRagSearchRepository(
                results = listOf(
                    result("first", 0.9f),
                    result("second", 0.8f),
                    result("third", 0.7f),
                ),
            ),
            reranker = RecordingReranker(
                scores = mapOf(
                    "first" to 0.2f,
                    "second" to 0.95f,
                    "third" to 0.5f,
                ),
            ),
        )

        val response = interactor.search(
            query(
                RagRetrievalConfig(
                    topKAfterFilter = 2,
                    isRerankingEnabled = true,
                ),
            ),
        )

        assertEquals(listOf("second", "third"), response.results.map { it.chunkId })
        assertEquals(3, response.rerankedResultsCount)
    }

    @Test
    fun processorCanBeReplaced() = runTest {
        val interactor = RagQueryInteractor(
            embeddingProvider = RecordingEmbeddingProvider(),
            searchRepository = RecordingRagSearchRepository(
                results = listOf(result("first", 0.9f)),
            ),
            resultProcessor = RagResultProcessor { _, _ -> listOf(result("reranked", 1f)) },
        )

        val response = interactor.search(query(RagRetrievalConfig()))

        assertEquals(listOf("reranked"), response.results.map { it.chunkId })
    }

    private fun query(config: RagRetrievalConfig): RagQuery =
        RagQuery(
            strategy = ChunkingStrategyType.Structure,
            indexDirectory = "/tmp/rag",
            question = "question",
            retrievalConfig = config,
        )

    private fun result(id: String, score: Float): RagSearchResult =
        RagSearchResult(
            source = "$id.md",
            title = "$id.md",
            section = "Section",
            chunkId = id,
            text = id,
            score = score,
        )
}

private class RecordingReranker(
    private val scores: Map<String, Float> = emptyMap(),
) : RagReranker {
    var lastResults: List<RagSearchResult> = emptyList()
        private set

    override suspend fun rerank(
        question: String,
        results: List<RagSearchResult>,
    ): List<RagSearchResult> {
        lastResults = results
        return results.map { result ->
            val score = scores[result.chunkId] ?: result.score
            result.copy(
                rerankScore = score,
                rerankRawScore = score,
            )
        }
    }
}

private class RecordingEmbeddingProvider : EmbeddingProvider {
    override suspend fun embed(text: String): FloatArray = floatArrayOf(1f, 0f)
}

private class RecordingRagSearchRepository(
    private val results: List<RagSearchResult> = emptyList(),
) : RagSearchRepository {
    var lastLimit: Int? = null
        private set

    override suspend fun search(
        indexDirectory: String,
        strategy: ChunkingStrategyType,
        queryText: String,
        queryEmbedding: FloatArray,
        limit: Int,
    ): List<RagSearchResult> {
        lastLimit = limit
        return results.take(limit)
    }
}
