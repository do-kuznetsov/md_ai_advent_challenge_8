package com.sibgear.rag.data.embedding

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxValue
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.sibgear.rag.domain.repository.EmbeddingProvider
import java.io.File
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OnnxNomicEmbeddingProvider(
    modelDirectory: String,
    private val maxLength: Int = DefaultMaxLength,
    private val engineFactory: OnnxNomicEmbeddingEngineFactory = DefaultOnnxNomicEmbeddingEngineFactory,
) : EmbeddingProvider,
    AutoCloseable {

    private val modelDirectoryFile = File(modelDirectory)
    private val modelFile = File(modelDirectoryFile, ModelFileName)
    private val tokenizerFile = File(modelDirectoryFile, TokenizerFileName)
    private var engineInstance: OnnxNomicEmbeddingEngine? = null
    private val engine: OnnxNomicEmbeddingEngine
        get() = engineInstance ?: createEngine().also { engineInstance = it }

    override suspend fun embed(text: String): FloatArray =
        withContext(Dispatchers.Default) {
            val embeddingEngine = engine
            runCatching {
                embeddingEngine.embed(text).l2Normalize()
            }.getOrElse { error ->
                throw IllegalStateException(
                    "embedding failed: ${error.message ?: error::class.simpleName ?: "unknown"}",
                    error,
                )
            }
        }

    override fun close() {
        engineInstance?.close()
        engineInstance = null
    }

    private fun createEngine(): OnnxNomicEmbeddingEngine {
        validateModelFiles()
        return engineFactory.create(
            modelFile = modelFile,
            tokenizerFile = tokenizerFile,
            maxLength = maxLength,
        )
    }

    private fun validateModelFiles() {
        val missingFiles = listOf(modelFile, tokenizerFile)
            .filterNot(File::exists)
            .joinToString { it.absolutePath }
        require(missingFiles.isEmpty()) {
            "embedding model files not found: $missingFiles"
        }
    }

    private companion object {
        private const val DefaultMaxLength = 8192
        private const val ModelFileName = "model.onnx"
        private const val TokenizerFileName = "tokenizer.json"
    }
}

interface OnnxNomicEmbeddingEngine : AutoCloseable {
    fun embed(text: String): FloatArray
}

fun interface OnnxNomicEmbeddingEngineFactory {
    fun create(
        modelFile: File,
        tokenizerFile: File,
        maxLength: Int,
    ): OnnxNomicEmbeddingEngine
}

object DefaultOnnxNomicEmbeddingEngineFactory : OnnxNomicEmbeddingEngineFactory {
    override fun create(
        modelFile: File,
        tokenizerFile: File,
        maxLength: Int,
    ): OnnxNomicEmbeddingEngine =
        DefaultOnnxNomicEmbeddingEngine(
            modelFile = modelFile,
            tokenizerFile = tokenizerFile,
            maxLength = maxLength,
        )
}

private class DefaultOnnxNomicEmbeddingEngine(
    modelFile: File,
    tokenizerFile: File,
    private val maxLength: Int,
) : OnnxNomicEmbeddingEngine {

    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession = environment.createSession(
        modelFile.absolutePath,
        OrtSession.SessionOptions(),
    )
    private val tokenizer: HuggingFaceTokenizer = HuggingFaceTokenizer.newInstance(tokenizerFile.toPath())
    private val lock = Any()

    override fun embed(text: String): FloatArray {
        val input = tokenizer.encodeEmbeddingText(text).trimTo(maxLength)
        val tensors = buildTensors(input)
        return try {
            synchronized(lock) {
                session.run(tensors).use { output ->
                    output.readEmbedding(input.attentionMask)
                }
            }
        } finally {
            tensors.values.forEach(OnnxTensor::close)
        }
    }

    override fun close() {
        session.close()
        tokenizer.close()
    }

    private fun buildTensors(input: EncodedEmbeddingInput): Map<String, OnnxTensor> {
        val tensors = linkedMapOf<String, OnnxTensor>()
        val inputNames = session.inputNames
        tensors["input_ids"] = OnnxTensor.createTensor(environment, arrayOf(input.inputIds))
        tensors["attention_mask"] = OnnxTensor.createTensor(environment, arrayOf(input.attentionMask))
        if ("token_type_ids" in inputNames) {
            tensors["token_type_ids"] = OnnxTensor.createTensor(environment, arrayOf(input.tokenTypeIds))
        }
        return tensors
    }
}

private fun HuggingFaceTokenizer.encodeEmbeddingText(text: String): EncodedEmbeddingInput {
    val encoding = encode(text)
    return EncodedEmbeddingInput(
        inputIds = encoding.embeddingLongArrayFrom("getIds"),
        attentionMask = encoding.embeddingLongArrayFrom("getAttentionMask"),
        tokenTypeIds = encoding.embeddingLongArrayFromOrZeros("getTypeIds"),
    )
}

private fun Any.embeddingLongArrayFrom(methodName: String): LongArray {
    val value = javaClass.getMethod(methodName).invoke(this)
    return value.toEmbeddingLongArrayValue(methodName)
}

private fun Any.embeddingLongArrayFromOrZeros(methodName: String): LongArray =
    runCatching { embeddingLongArrayFrom(methodName) }.getOrElse {
        LongArray(embeddingLongArrayFrom("getIds").size)
    }

private fun Any?.toEmbeddingLongArrayValue(methodName: String): LongArray =
    when (this) {
        is LongArray -> this
        is IntArray -> LongArray(size) { index -> this[index].toLong() }
        is Array<*> -> LongArray(size) { index -> (this[index] as Number).toLong() }
        else -> error("Unsupported tokenizer $methodName result: ${this?.javaClass?.name ?: "null"}.")
    }

private data class EncodedEmbeddingInput(
    val inputIds: LongArray,
    val attentionMask: LongArray,
    val tokenTypeIds: LongArray,
) {
    fun trimTo(maxLength: Int): EncodedEmbeddingInput =
        if (inputIds.size <= maxLength) {
            this
        } else {
            copy(
                inputIds = inputIds.copyOf(maxLength),
                attentionMask = attentionMask.copyOf(maxLength),
                tokenTypeIds = tokenTypeIds.copyOf(maxLength),
            )
        }
}

private fun OrtSession.Result.readEmbedding(attentionMask: LongArray): FloatArray {
    val values = (0 until size()).map { index -> get(index).value }
    values.firstNotNullOfOrNull { value -> value.asPooledVector() }?.let { return it }
    val tokenEmbeddings = values.firstNotNullOfOrNull { value -> value.asTokenEmbeddings(attentionMask.size) }
        ?: error("Unsupported embedding ONNX output shape: ${values.firstOrNull()?.javaClass?.name ?: "null"}.")
    return tokenEmbeddings.meanPool(attentionMask)
}

private fun OnnxValue.asPooledVector(): FloatArray? =
    value.asPooledVector()

private fun Any?.asPooledVector(): FloatArray? =
    when (this) {
        is FloatArray -> this
        is DoubleArray -> FloatArray(size) { index -> this[index].toFloat() }
        is Array<*> -> {
            if (size == 1) {
                this[0].asPooledVector()
            } else {
                null
            }
        }
        else -> null
    }

private fun Any?.asTokenEmbeddings(maskLength: Int): Array<FloatArray>? =
    when (this) {
        is Array<*> -> {
            val nested = firstOrNull()
            when {
                nested is Array<*> -> nested.toFloatArrayRows()
                all { it is FloatArray || it is DoubleArray } && size == maskLength -> toFloatArrayRows()
                else -> null
            }
        }
        else -> null
    }

private fun Array<*>.toFloatArrayRows(): Array<FloatArray>? =
    mapNotNull { row ->
        when (row) {
            is FloatArray -> row
            is DoubleArray -> FloatArray(row.size) { index -> row[index].toFloat() }
            else -> null
        }
    }.takeIf { it.size == size }?.toTypedArray()

private fun Array<FloatArray>.meanPool(attentionMask: LongArray): FloatArray {
    require(isNotEmpty()) { "embedding output is empty." }
    val dimension = first().size
    val pooled = FloatArray(dimension)
    var count = 0
    forEachIndexed { index, row ->
        if (attentionMask.getOrElse(index) { 1L } == 0L) {
            return@forEachIndexed
        }
        for (dimensionIndex in 0 until dimension) {
            pooled[dimensionIndex] += row[dimensionIndex]
        }
        count += 1
    }
    val divisor = count.coerceAtLeast(1).toFloat()
    for (index in pooled.indices) {
        pooled[index] /= divisor
    }
    return pooled
}

internal fun FloatArray.l2Normalize(): FloatArray {
    var norm = 0.0
    forEach { value -> norm += value * value }
    val divisor = sqrt(norm).takeIf { it > 0.0 } ?: return this
    return FloatArray(size) { index -> (this[index] / divisor).toFloat() }
}
