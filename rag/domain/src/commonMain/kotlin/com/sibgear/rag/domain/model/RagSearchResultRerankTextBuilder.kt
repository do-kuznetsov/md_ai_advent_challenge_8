package com.sibgear.rag.domain.model

object RagSearchResultRerankTextBuilder {
    fun build(result: RagSearchResult): String =
        buildString {
            appendLine("# ${result.section}")
            appendLine()
            appendLine("Документ: ${result.title}")
            appendLine("Источник: ${result.source}")
            appendLine()
            append(result.text)
        }.trimEnd()
}
