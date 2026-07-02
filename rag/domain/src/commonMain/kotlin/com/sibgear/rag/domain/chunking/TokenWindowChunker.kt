package com.sibgear.rag.domain.chunking

import com.sibgear.rag.domain.model.ChunkingConfig
import com.sibgear.rag.domain.model.ChunkingStrategyType
import com.sibgear.rag.domain.model.DocumentChunk
import com.sibgear.rag.domain.model.SourceDocument

internal object TokenWindowChunker {
    fun chunk(
        document: SourceDocument,
        config: ChunkingConfig,
        strategy: ChunkingStrategyType,
        section: String,
        text: String,
        chunkIdPrefix: String,
    ): List<DocumentChunk> {
        val tokens = text.toTokens()
        if (tokens.isEmpty()) {
            return emptyList()
        }

        val step = config.chunkSize - config.overlapSize
        val chunks = mutableListOf<DocumentChunk>()
        var start = 0
        var index = 1
        while (start < tokens.size) {
            val endExclusive = minOf(start + config.chunkSize, tokens.size)
            val chunkTokens = tokens.subList(start, endExclusive)
            chunks += DocumentChunk(
                source = document.source,
                title = document.title,
                section = section,
                chunkId = "$chunkIdPrefix-$index",
                text = chunkTokens.joinToString(" "),
                strategy = strategy,
                startToken = start + 1,
                endToken = endExclusive,
            )
            if (endExclusive == tokens.size) {
                break
            }
            start += step
            index += 1
        }
        return chunks
    }

    internal fun String.toTokens(): List<String> =
        trim()
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)
}
