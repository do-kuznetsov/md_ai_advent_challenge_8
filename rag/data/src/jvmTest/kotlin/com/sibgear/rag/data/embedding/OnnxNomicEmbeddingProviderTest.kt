package com.sibgear.rag.data.embedding

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OnnxNomicEmbeddingProviderTest {
    @Test
    fun missingModelFilesReturnClearError() = runTest {
        val provider = OnnxNomicEmbeddingProvider(
            modelDirectory = createTempDirectory().toFile().absolutePath,
            engineFactory = RecordingEmbeddingEngineFactory(),
        )

        val error = assertFailsWith<IllegalArgumentException> {
            provider.embed("question")
        }

        assertTrue(error.message.orEmpty().contains("embedding model files not found"))
    }

    @Test
    fun embedReturnsNormalizedVector() = runTest {
        val modelDirectory = createModelDirectory()
        val provider = OnnxNomicEmbeddingProvider(
            modelDirectory = modelDirectory.absolutePath,
            engineFactory = RecordingEmbeddingEngineFactory(
                RecordingEmbeddingEngine(floatArrayOf(3f, 4f)),
            ),
        )

        val embedding = provider.embed("hello")

        assertEquals(0.6f, embedding[0])
        assertEquals(0.8f, embedding[1])
    }

    @Test
    fun l2NormalizeKeepsZeroVector() {
        assertEquals(listOf(0f, 0f), floatArrayOf(0f, 0f).l2Normalize().toList())
        val normalized = floatArrayOf(1f, 1f).l2Normalize()
        val expected = (1.0 / sqrt(2.0)).toFloat()
        assertEquals(expected, normalized[0])
        assertEquals(expected, normalized[1])
    }

    private fun createModelDirectory(): File =
        createTempDirectory().toFile().also { directory ->
            File(directory, "model.onnx").writeText("test")
            File(directory, "tokenizer.json").writeText("test")
        }
}

private class RecordingEmbeddingEngineFactory(
    private val engine: RecordingEmbeddingEngine = RecordingEmbeddingEngine(),
) : OnnxNomicEmbeddingEngineFactory {
    override fun create(
        modelFile: File,
        tokenizerFile: File,
        maxLength: Int,
    ): OnnxNomicEmbeddingEngine =
        engine
}

private class RecordingEmbeddingEngine(
    private val vector: FloatArray = floatArrayOf(1f, 0f),
) : OnnxNomicEmbeddingEngine {
    override fun embed(text: String): FloatArray = vector

    override fun close() = Unit
}
