package com.sibgear.rag.domain.interactor

import com.sibgear.rag.domain.chunking.ChunkingStrategy
import com.sibgear.rag.domain.model.ChunkingConfig
import com.sibgear.rag.domain.model.ChunkingStrategyType
import com.sibgear.rag.domain.model.DocumentChunk
import com.sibgear.rag.domain.model.DocumentScanResult
import com.sibgear.rag.domain.model.EmbeddedDocumentChunk
import com.sibgear.rag.domain.model.RagIndexRun
import com.sibgear.rag.domain.model.SourceDocument
import com.sibgear.rag.domain.repository.DocumentScanner
import com.sibgear.rag.domain.repository.EmbeddingProvider
import com.sibgear.rag.domain.repository.RagIndexRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RagIndexingInteractorTest {
    @Test
    fun indexEmbedsMetadataEnrichedTextAndStoresOriginalChunkText() = runTest {
        val chunk = DocumentChunk(
            source = "migrate/compile_feature-module.md",
            title = "compile_feature-module.md",
            section = "Типичные ошибки компиляции и способы их устранения",
            chunkId = "chunk-1",
            text = "* использование import ru.tander.omni.util.*",
            strategy = ChunkingStrategyType.Structure,
            startToken = 1,
            endToken = 5,
        )
        val embeddingProvider = RecordingIndexEmbeddingProvider()
        val repository = RecordingIndexRepository()
        val interactor = RagIndexingInteractor(
            scanner = SingleDocumentScanner(),
            chunkingStrategy = StaticChunkingStrategy(chunk),
            embeddingProvider = embeddingProvider,
            indexRepository = repository,
        )

        interactor.index(
            RagIndexRun(
                inputPath = "/tmp/docs",
                strategy = ChunkingStrategyType.Structure,
                chunkingConfig = ChunkingConfig(chunkSize = 500, overlapSize = 50),
                model = "test",
            ),
        )

        val embeddedText = embeddingProvider.texts.single()
        assertTrue(embeddedText.contains("# Типичные ошибки компиляции и способы их устранения"))
        assertTrue(embeddedText.contains("Документ: compile_feature-module.md"))
        assertTrue(embeddedText.contains("Источник: migrate/compile_feature-module.md"))
        assertTrue(embeddedText.endsWith("* использование import ru.tander.omni.util.*"))
        assertEquals("* использование import ru.tander.omni.util.*", repository.chunks.single().chunk.text)
    }
}

private class SingleDocumentScanner : DocumentScanner {
    override suspend fun scan(inputPath: String): DocumentScanResult =
        DocumentScanResult(
            documents = listOf(
                SourceDocument(
                    source = "migrate/compile_feature-module.md",
                    title = "compile_feature-module.md",
                    text = "# Собрать feature-module",
                    contentSha256 = "sha",
                ),
            ),
            warnings = emptyList(),
        )
}

private class StaticChunkingStrategy(
    private val chunk: DocumentChunk,
) : ChunkingStrategy {
    override val type: ChunkingStrategyType = chunk.strategy

    override fun chunk(
        document: SourceDocument,
        config: ChunkingConfig,
    ): List<DocumentChunk> = listOf(chunk)
}

private class RecordingIndexEmbeddingProvider : EmbeddingProvider {
    val texts = mutableListOf<String>()

    override suspend fun embed(text: String): FloatArray {
        texts += text
        return floatArrayOf(1f, 0f)
    }
}

private class RecordingIndexRepository : RagIndexRepository {
    var chunks: List<EmbeddedDocumentChunk> = emptyList()
        private set

    override suspend fun recreate() = Unit

    override suspend fun save(
        run: RagIndexRun,
        documents: List<SourceDocument>,
        chunks: List<EmbeddedDocumentChunk>,
    ): Long {
        this.chunks = chunks
        return 0L
    }
}
