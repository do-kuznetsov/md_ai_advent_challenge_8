package com.sibgear.rag.data.rerank

import com.sibgear.rag.domain.model.RagSearchResult
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OnnxBgeRerankerTest {
    @Test
    fun missingModelFilesReturnClearError() = runTest {
        val reranker = OnnxBgeReranker(
            modelDirectory = createTempDirectory().toFile().absolutePath,
            engineFactory = RecordingEngineFactory(),
        )

        val error = assertFailsWith<IllegalArgumentException> {
            reranker.rerank("question", listOf(result("chunk")))
        }

        assertTrue(error.message.orEmpty().contains("reranker model files not found"))
    }

    @Test
    fun rerankWritesRawAndNormalizedScores() = runTest {
        val modelDirectory = createModelDirectory()
        val engine = RecordingEngine(rawScore = 2f)
        val reranker = OnnxBgeReranker(
            modelDirectory = modelDirectory.absolutePath,
            engineFactory = RecordingEngineFactory(engine),
        )

        val results = reranker.rerank(
            "question",
            listOf(
                result(
                    id = "chunk",
                    source = "migrate/compile_feature-module.md",
                    title = "compile_feature-module.md",
                    section = "Типичные ошибки компиляции и способы их устранения",
                    text = "* использование import ru.tander.omni.util.*",
                ),
            ),
        )

        assertEquals(2f, results.single().rerankRawScore)
        assertEquals(sigmoid(2f), results.single().rerankScore)
        assertEquals("question", engine.lastQuery)
        assertTrue(engine.lastDocument.orEmpty().contains("# Типичные ошибки компиляции и способы их устранения"))
        assertTrue(engine.lastDocument.orEmpty().contains("Документ: compile_feature-module.md"))
        assertTrue(engine.lastDocument.orEmpty().contains("Источник: migrate/compile_feature-module.md"))
        assertTrue(engine.lastDocument.orEmpty().endsWith("* использование import ru.tander.omni.util.*"))
    }

    @Test
    fun sigmoidNormalizesScore() {
        assertEquals(0.5f, sigmoid(0f))
        assertTrue(sigmoid(8f) > 0.99f)
        assertTrue(sigmoid(-8f) < 0.01f)
    }

    private fun createModelDirectory(): File =
        createTempDirectory().toFile().also { directory ->
            File(directory, "model.onnx").writeText("test")
            File(directory, "tokenizer.json").writeText("test")
        }

    private fun result(
        id: String,
        source: String = "$id.md",
        title: String = "$id.md",
        section: String = "Section",
        text: String = id,
    ): RagSearchResult =
        RagSearchResult(
            source = source,
            title = title,
            section = section,
            chunkId = id,
            text = text,
            score = 0.8f,
        )
}

private class RecordingEngineFactory(
    private val engine: RecordingEngine = RecordingEngine(),
) : OnnxBgeRerankerEngineFactory {
    override fun create(
        modelFile: File,
        tokenizerFile: File,
        maxLength: Int,
    ): OnnxBgeRerankerEngine =
        engine
}

private class RecordingEngine(
    private val rawScore: Float = 1f,
) : OnnxBgeRerankerEngine {
    var lastQuery: String? = null
        private set
    var lastDocument: String? = null
        private set

    override fun score(query: String, document: String): Float {
        lastQuery = query
        lastDocument = document
        return rawScore
    }

    override fun close() = Unit
}
