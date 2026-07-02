package com.sibgear.rag.domain.chunking

import com.sibgear.rag.domain.model.ChunkingConfig
import com.sibgear.rag.domain.model.ChunkingStrategyType
import com.sibgear.rag.domain.model.DocumentChunk
import com.sibgear.rag.domain.model.SourceDocument

class StructureChunkingStrategy : ChunkingStrategy {
    override val type: ChunkingStrategyType = ChunkingStrategyType.Structure

    override fun chunk(
        document: SourceDocument,
        config: ChunkingConfig,
    ): List<DocumentChunk> =
        document.sections().flatMapIndexed { sectionIndex, section ->
            TokenWindowChunker.chunk(
                document = document,
                config = config,
                strategy = type,
                section = section.title,
                text = section.text,
                chunkIdPrefix = "${document.source}#${type.cliName}-section-${sectionIndex + 1}",
            )
        }

    private fun SourceDocument.sections(): List<Section> {
        val lines = text.lineSequence().toList()
        if (lines.none { it.isMarkdownHeading() }) {
            return listOf(Section(title = title, text = text))
        }

        val sections = mutableListOf<Section>()
        var currentTitle = title
        val currentText = StringBuilder()

        fun flush() {
            val sectionText = currentText.toString().trim()
            if (sectionText.isNotBlank()) {
                sections += Section(currentTitle, sectionText)
            }
            currentText.clear()
        }

        lines.forEach { line ->
            if (line.isMarkdownHeading()) {
                flush()
                currentTitle = line.trim().removePrefixHashes()
            } else {
                currentText.appendLine(line)
            }
        }
        flush()

        return sections.ifEmpty { listOf(Section(title = title, text = text)) }
    }

    private fun String.isMarkdownHeading(): Boolean =
        trimStart().matches(Regex("#{1,6}\\s+.+"))

    private fun String.removePrefixHashes(): String =
        replace(Regex("^#{1,6}\\s+"), "").trim()

    private data class Section(
        val title: String,
        val text: String,
    )
}
