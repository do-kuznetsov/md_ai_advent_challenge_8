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
        val reranker = OnnxBgeReranker(
            modelDirectory = modelDirectory.absolutePath,
            engineFactory = RecordingEngineFactory(rawScore = 2f),
        )

        val results = reranker.rerank("question", listOf(result("chunk")))

        assertEquals(2f, results.single().rerankRawScore)
        assertEquals(sigmoid(2f), results.single().rerankScore)
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

    private fun result(id: String): RagSearchResult =
        RagSearchResult(
            source = "$id.md",
            title = "$id.md",
            section = "Section",
            chunkId = id,
            text = id,
            score = 0.8f,
        )
}

private class RecordingEngineFactory(
    private val rawScore: Float = 1f,
) : OnnxBgeRerankerEngineFactory {
    override fun create(
        modelFile: File,
        tokenizerFile: File,
        maxLength: Int,
    ): OnnxBgeRerankerEngine =
        RecordingEngine(rawScore)
}

private class RecordingEngine(
    private val rawScore: Float,
) : OnnxBgeRerankerEngine {
    override fun score(query: String, document: String): Float = rawScore

    override fun close() = Unit
}
