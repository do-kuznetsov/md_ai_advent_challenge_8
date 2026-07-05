package com.sibgear.rag.domain.interactor

import com.sibgear.rag.domain.chunking.ChunkingStrategy
import com.sibgear.rag.domain.model.DocumentChunkEmbeddingTextBuilder
import com.sibgear.rag.domain.model.EmbeddedDocumentChunk
import com.sibgear.rag.domain.model.RagIndexRun
import com.sibgear.rag.domain.model.RagIndexSummary
import com.sibgear.rag.domain.repository.DocumentScanner
import com.sibgear.rag.domain.repository.EmbeddingProvider
import com.sibgear.rag.domain.repository.RagIndexRepository
import kotlin.system.measureTimeMillis

class RagIndexingInteractor(
    private val scanner: DocumentScanner,
    private val chunkingStrategy: ChunkingStrategy,
    private val embeddingProvider: EmbeddingProvider,
    private val indexRepository: RagIndexRepository,
) {
    suspend fun index(run: RagIndexRun): RagIndexSummary {
        lateinit var summary: RagIndexSummary
        val elapsed = measureTimeMillis {
            val scanResult = scanner.scan(run.inputPath)
            val chunks = scanResult.documents.flatMap { document ->
                chunkingStrategy.chunk(document, run.chunkingConfig)
            }
            val embeddedChunks = chunks.map { chunk ->
                EmbeddedDocumentChunk(
                    chunk = chunk,
                    embedding = embeddingProvider.embed(DocumentChunkEmbeddingTextBuilder.build(chunk)),
                )
            }

            indexRepository.recreate()
            val databaseSizeBytes = indexRepository.save(
                run = run,
                documents = scanResult.documents,
                chunks = embeddedChunks,
            )

            summary = RagIndexSummary(
                strategy = run.strategy,
                files = scanResult.documents.size,
                chunks = chunks.size,
                embeddingDimension = embeddedChunks.firstOrNull()?.embedding?.size ?: 0,
                elapsedMs = 0L,
                databaseSizeBytes = databaseSizeBytes,
                warnings = scanResult.warnings,
            )
        }
        return summary.copy(elapsedMs = elapsed)
    }
}
