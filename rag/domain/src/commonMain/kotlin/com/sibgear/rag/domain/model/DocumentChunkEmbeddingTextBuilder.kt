package com.sibgear.rag.domain.model

object DocumentChunkEmbeddingTextBuilder {
    fun build(chunk: DocumentChunk): String =
        buildString {
            appendLine("# ${chunk.section}")
            appendLine()
            appendLine("Документ: ${chunk.title}")
            appendLine("Источник: ${chunk.source}")
            appendLine()
            append(chunk.text)
        }.trimEnd()
}
