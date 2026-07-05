package com.sibgear.rag.data.rerank

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxValue
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.sibgear.rag.domain.model.RagSearchResult
import com.sibgear.rag.domain.model.RagSearchResultRerankTextBuilder
import com.sibgear.rag.domain.repository.RagReranker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.exp

class OnnxBgeReranker(
    modelDirectory: String,
    private val maxLength: Int = DefaultMaxLength,
    private val engineFactory: OnnxBgeRerankerEngineFactory = DefaultOnnxBgeRerankerEngineFactory,
) : RagReranker,
    AutoCloseable {

    private val modelDirectoryFile = File(modelDirectory)
    private val modelFile = File(modelDirectoryFile, ModelFileName)
    private val tokenizerFile = File(modelDirectoryFile, TokenizerFileName)
    private var engineInstance: OnnxBgeRerankerEngine? = null
    private val engine: OnnxBgeRerankerEngine
        get() = engineInstance ?: createEngine().also { engineInstance = it }

    override suspend fun rerank(
        question: String,
        results: List<RagSearchResult>,
    ): List<RagSearchResult> =
        withContext(Dispatchers.Default) {
            if (results.isEmpty()) {
                return@withContext emptyList()
            }

            val rerankerEngine = engine
            runCatching {
                results.map { result ->
                    val rawScore = rerankerEngine.score(
                        query = question,
                        document = RagSearchResultRerankTextBuilder.build(result),
                    )
                    result.copy(
                        rerankRawScore = rawScore,
                        rerankScore = sigmoid(rawScore),
                    )
                }
            }.getOrElse { error ->
                throw IllegalStateException(
                    "rerank failed: ${error.message ?: error::class.simpleName ?: "unknown"}",
                    error,
                )
            }
        }

    override fun close() {
        engineInstance?.close()
        engineInstance = null
    }

    private fun createEngine(): OnnxBgeRerankerEngine {
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
            "reranker model files not found: $missingFiles"
        }
    }

    private companion object {
        private const val DefaultMaxLength = 512
        private const val ModelFileName = "model.onnx"
        private const val TokenizerFileName = "tokenizer.json"
    }
}

internal fun sigmoid(value: Float): Float =
    (1.0 / (1.0 + exp(-value.toDouble()))).toFloat()

interface OnnxBgeRerankerEngine : AutoCloseable {
    fun score(query: String, document: String): Float
}

fun interface OnnxBgeRerankerEngineFactory {
    fun create(
        modelFile: File,
        tokenizerFile: File,
        maxLength: Int,
    ): OnnxBgeRerankerEngine
}

object DefaultOnnxBgeRerankerEngineFactory : OnnxBgeRerankerEngineFactory {
    override fun create(
        modelFile: File,
        tokenizerFile: File,
        maxLength: Int,
    ): OnnxBgeRerankerEngine =
        DefaultOnnxBgeRerankerEngine(
            modelFile = modelFile,
            tokenizerFile = tokenizerFile,
            maxLength = maxLength,
        )
}

private class DefaultOnnxBgeRerankerEngine(
    modelFile: File,
    tokenizerFile: File,
    private val maxLength: Int,
) : OnnxBgeRerankerEngine {

    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession = environment.createSession(
        modelFile.absolutePath,
        OrtSession.SessionOptions(),
    )
    private val tokenizer: HuggingFaceTokenizer = HuggingFaceTokenizer.newInstance(tokenizerFile.toPath())
    private val lock = Any()

    override fun score(query: String, document: String): Float {
        val input = tokenizer.encodePair(query, document).trimTo(maxLength)
        val tensors = buildTensors(input)
        return try {
            synchronized(lock) {
                session.run(tensors).use { output ->
                    output.readFirstLogit()
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

    private fun buildTensors(input: EncodedRerankerInput): Map<String, OnnxTensor> {
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

private fun HuggingFaceTokenizer.encodePair(
    query: String,
    document: String,
): EncodedRerankerInput {
    val encodeMethod = javaClass.methods.firstOrNull { method ->
        method.name == "encode" &&
            method.parameterTypes.size == 2 &&
            method.parameterTypes.all { it == String::class.java }
    }
    val encoding = if (encodeMethod != null) {
        encodeMethod.invoke(this, query, document)
    } else {
        encode("$query\n\n$document")
    }
    return EncodedRerankerInput(
        inputIds = encoding.longArrayFrom("getIds"),
        attentionMask = encoding.longArrayFrom("getAttentionMask"),
        tokenTypeIds = encoding.longArrayFromOrZeros("getTypeIds"),
    )
}

private fun Any.longArrayFrom(methodName: String): LongArray {
    val value = javaClass.getMethod(methodName).invoke(this)
    return value.toLongArrayValue(methodName)
}

private fun Any.longArrayFromOrZeros(methodName: String): LongArray =
    runCatching { longArrayFrom(methodName) }.getOrElse {
        LongArray(longArrayFrom("getIds").size)
    }

private fun Any?.toLongArrayValue(methodName: String): LongArray =
    when (this) {
        is LongArray -> this
        is IntArray -> LongArray(size) { index -> this[index].toLong() }
        is Array<*> -> LongArray(size) { index -> (this[index] as Number).toLong() }
        else -> error("Unsupported tokenizer $methodName result: ${this?.javaClass?.name ?: "null"}.")
    }

private data class EncodedRerankerInput(
    val inputIds: LongArray,
    val attentionMask: LongArray,
    val tokenTypeIds: LongArray,
) {
    fun trimTo(maxLength: Int): EncodedRerankerInput =
        if (inputIds.size <= maxLength) {
            this
        } else {
            copy(
                inputIds = inputIds.copyOf(maxLength),
                attentionMask = attentionMask.copyOf(maxLength),
                tokenTypeIds = tokenTypeIds.copyOf(maxLength),
            )
        }

    override fun equals(other: Any?): Boolean =
        other is EncodedRerankerInput &&
            inputIds.contentEquals(other.inputIds) &&
            attentionMask.contentEquals(other.attentionMask) &&
            tokenTypeIds.contentEquals(other.tokenTypeIds)

    override fun hashCode(): Int {
        var result = inputIds.contentHashCode()
        result = 31 * result + attentionMask.contentHashCode()
        result = 31 * result + tokenTypeIds.contentHashCode()
        return result
    }
}

private fun OrtSession.Result.readFirstLogit(): Float {
    val value = get(0).value
    return value.firstFloatOrNull()
        ?: error("Unsupported reranker ONNX output shape: ${value?.javaClass?.name ?: "null"}.")
}

private fun OnnxValue.firstFloatOrNull(): Float? =
    value.firstFloatOrNull()

private fun Any?.firstFloatOrNull(): Float? =
    when (this) {
        is Float -> this
        is Double -> toFloat()
        is FloatArray -> firstOrNull()
        is DoubleArray -> firstOrNull()?.toFloat()
        is Array<*> -> firstOrNullNotNull { it.firstFloatOrNull() }
        else -> null
    }

private inline fun <T> Array<T>.firstOrNullNotNull(block: (T) -> Float?): Float? {
    for (item in this) {
        val value = block(item)
        if (value != null) {
            return value
        }
    }
    return null
}
