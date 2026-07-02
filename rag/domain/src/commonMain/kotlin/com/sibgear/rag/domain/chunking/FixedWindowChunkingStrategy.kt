package com.sibgear.rag.domain.chunking

import com.sibgear.rag.domain.model.ChunkingConfig
import com.sibgear.rag.domain.model.ChunkingStrategyType
import com.sibgear.rag.domain.model.DocumentChunk
import com.sibgear.rag.domain.model.SourceDocument

class FixedWindowChunkingStrategy : ChunkingStrategy {
    override val type: ChunkingStrategyType = ChunkingStrategyType.Fixed

    override fun chunk(
        document: SourceDocument,
        config: ChunkingConfig,
    ): List<DocumentChunk> =
        TokenWindowChunker.chunk(
            document = document,
            config = config,
            strategy = type,
            section = document.title,
            text = document.text,
            chunkIdPrefix = "${document.source}#${type.cliName}",
        )
}
