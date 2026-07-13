package com.sibgear.server

import com.sibgear.rag.data.embedding.OnnxNomicEmbeddingProvider
import com.sibgear.rag.data.rerank.OnnxBgeReranker
import com.sibgear.rag.data.sqlite.SQLiteRagSearchRepository
import com.sibgear.rag.domain.interactor.RagQueryInteractor
import com.sibgear.rag.domain.model.ChunkingStrategyType
import com.sibgear.rag.domain.model.RagQuery
import com.sibgear.rag.domain.model.RagQueryResult
import com.sibgear.rag.domain.model.RagRetrievalConfig
import com.sibgear.rag.domain.model.RagSearchResult
import com.sibgear.server.protocol.ServerRagSettings
import com.sibgear.server.protocol.ServerRagStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RagChatService(
    config: ServerConfig,
    private val interactor: RagQueryInteractor = RagQueryInteractor(
        embeddingProvider = OnnxNomicEmbeddingProvider(config.embeddingModelDirectory),
        searchRepository = SQLiteRagSearchRepository(),
    ),
    private val indexDirectory: String = config.ragIndexDirectory,
    private val rerankerFactory: () -> OnnxBgeReranker = {
        OnnxBgeReranker(config.rerankerModelDirectory)
    },
) {
    private var reranker: OnnxBgeReranker? = null

    suspend fun findContext(
        prompt: String,
        settings: ServerRagSettings,
        rewrite: suspend (String) -> String,
    ): RagContext? {
        if (!settings.isEnabled) {
            return null
        }
        val normalized = settings.normalized()
        val rewritten = if (normalized.isQueryRewriteEnabled) {
            rewrite(prompt).takeIf { it.isNotBlank() && it != prompt }
        } else {
            null
        }
        val question = rewritten ?: prompt
        val reranker = if (normalized.isRerankingEnabled) {
            reranker ?: rerankerFactory().also { reranker = it }
        } else {
            null
        }
        val result = interactor.search(
            query = RagQuery(
                strategy = normalized.strategy.toDomain(),
                indexDirectory = indexDirectory,
                question = question,
                retrievalConfig = normalized.toRetrievalConfig(),
                rewrittenQuestion = rewritten,
            ),
            reranker = reranker,
        )
        return RagContext(
            result = result,
            promptBlock = result.results.toPromptBlock(),
            status = result.toStatus(normalized),
        )
    }

    private suspend fun List<RagSearchResult>.toPromptBlock(): String =
        withContext(Dispatchers.Default) {
            joinToString(separator = "\n\n") { result ->
                buildString {
                    appendLine("[RAG_SOURCE]")
                    appendLine("title: ${result.title}")
                    appendLine("source: ${result.source}")
                    appendLine("section: ${result.section}")
                    appendLine("score: ${result.rerankScore ?: result.score}")
                    appendLine(result.text)
                }
            }
        }
}

data class RagContext(
    val result: RagQueryResult,
    val promptBlock: String,
    val status: String,
)

fun ServerRagSettings.normalized(): ServerRagSettings {
    val after = topKAfterFilter.takeIf { it > 0 } ?: 5
    val before = (topKBeforeFilter.takeIf { it > 0 } ?: 15).coerceAtLeast(after)
    return copy(
        topKBeforeFilter = before,
        topKAfterFilter = after,
        similarityThreshold = similarityThreshold.coerceIn(0f, 1f),
    )
}

private fun ServerRagSettings.toRetrievalConfig(): RagRetrievalConfig =
    RagRetrievalConfig(
        topKBeforeFilter = topKBeforeFilter,
        topKAfterFilter = topKAfterFilter,
        similarityThreshold = similarityThreshold,
        isFilteringEnabled = isFilteringEnabled,
        isRerankingEnabled = isRerankingEnabled,
    )

private fun ServerRagStrategy.toDomain(): ChunkingStrategyType =
    when (this) {
        ServerRagStrategy.Fixed -> ChunkingStrategyType.Fixed
        ServerRagStrategy.Structure -> ChunkingStrategyType.Structure
    }

private fun RagQueryResult.toStatus(settings: ServerRagSettings): String =
    buildString {
        append("RAG: raw=$rawResultsCount")
        if (settings.isFilteringEnabled) {
            append(", filtered=$filteredResultsCount")
        }
        if (settings.isRerankingEnabled) {
            append(", reranked=$rerankedResultsCount")
        }
        append(", used=${results.size}")
        rewrittenQuestion?.let { append(", rewritten") }
    }
