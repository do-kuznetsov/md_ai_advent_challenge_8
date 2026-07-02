package com.sibgear.rag.domain.chunking

import com.sibgear.rag.domain.model.ChunkingConfig
import com.sibgear.rag.domain.model.ChunkingStrategyType
import com.sibgear.rag.domain.model.DocumentChunk
import com.sibgear.rag.domain.model.SourceDocument

interface ChunkingStrategy {
    val type: ChunkingStrategyType

    fun chunk(
        document: SourceDocument,
        config: ChunkingConfig,
    ): List<DocumentChunk>
}
