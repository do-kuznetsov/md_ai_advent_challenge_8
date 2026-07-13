package com.sibgear.rag.app

import com.sibgear.rag.data.embedding.OllamaEmbeddingProvider
import com.sibgear.rag.data.filesystem.FileDocumentScanner
import com.sibgear.rag.data.sqlite.SQLiteRagIndexRepository
import com.sibgear.rag.domain.chunking.FixedWindowChunkingStrategy
import com.sibgear.rag.domain.chunking.StructureChunkingStrategy
import com.sibgear.rag.domain.interactor.RagIndexingInteractor
import com.sibgear.rag.domain.model.ChunkingConfig
import com.sibgear.rag.domain.model.ChunkingStrategyType
import com.sibgear.rag.domain.model.RagIndexRun
import com.sibgear.rag.domain.model.RagIndexSummary
import com.sibgear.rag.domain.repository.EmbeddingProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

fun main(args: Array<String>) = runBlocking {
    val options = CliOptions.parse(args).getOrElse { error ->
        println("Ошибка: ${error.message}")
        printUsage()
        exitProcess(1)
    }

    val inputDirectory = File(options.input).canonicalFile
    if (!inputDirectory.isDirectory) {
        println("Ошибка: input должен быть существующей директорией: ${inputDirectory.absolutePath}")
        exitProcess(1)
    }

    val outputDirectory = File(options.output).canonicalFile
    outputDirectory.mkdirs()
    if (!outputDirectory.isDirectory) {
        println("Ошибка: output директорию не удалось создать: ${outputDirectory.absolutePath}")
        exitProcess(1)
    }

    val chunkingConfig = ChunkingConfig(
        chunkSize = options.chunkSize,
        overlapSize = options.overlap,
    )
    val scanner = FileDocumentScanner()
    val strategies = options.strategy.toStrategies()

    options.createEmbeddingProvider().use { embeddingProvider ->
        val summaries = strategies.map { strategy ->
            val databaseFile = outputDirectory.resolve("rag-${strategy.cliName}.sqlite")
            val chunkingStrategy = when (strategy) {
                ChunkingStrategyType.Fixed -> FixedWindowChunkingStrategy()
                ChunkingStrategyType.Structure -> StructureChunkingStrategy()
            }
            RagIndexingInteractor(
                scanner = scanner,
                chunkingStrategy = chunkingStrategy,
                embeddingProvider = embeddingProvider,
                indexRepository = SQLiteRagIndexRepository(databaseFile),
            ).index(
                RagIndexRun(
                    inputPath = inputDirectory.absolutePath,
                    strategy = strategy,
                    chunkingConfig = chunkingConfig,
                    model = options.model,
                ),
            )
        }

        printComparison(summaries)
    }
}

private fun printComparison(summaries: List<RagIndexSummary>) {
    println("Comparison:")
    println("strategy | files | chunks | embedding_dimension | elapsed_ms | db_size_bytes")
    summaries.forEach { summary ->
        println(
            listOf(
                summary.strategy.cliName,
                summary.files,
                summary.chunks,
                summary.embeddingDimension,
                summary.elapsedMs,
                summary.databaseSizeBytes,
            ).joinToString(" | "),
        )
        summary.warnings.forEach { warning ->
            println("warning[${summary.strategy.cliName}]: $warning")
        }
    }
}

private fun printUsage() {
    println("Использование:")
    println(
        "./gradlew -q :rag:app:jvmRun --args='" +
            "--input ./docs --output ./rag-output --strategy both " +
            "--chunk-size 500 --overlap 50 --model nomic-embed-text " +
            "--embedding-backend ollama --ollama-url http://localhost:11434'",
    )
    println(
        "./gradlew -q :rag:app:jvmRun --args='" +
            "--input ./docs --output ./rag-output --strategy both " +
            "--embedding-backend onnx --embedding-model-dir ./rag/models/nomic-embed-text'",
    )
}

private data class CliOptions(
    val input: String,
    val output: String,
    val strategy: StrategyOption,
    val chunkSize: Int,
    val overlap: Int,
    val model: String,
    val ollamaUrl: String,
    val embeddingBackend: EmbeddingBackend,
    val embeddingModelDirectory: String,
) {
    fun createEmbeddingProvider(): AutoCloseableEmbeddingProvider =
        when (embeddingBackend) {
            EmbeddingBackend.Ollama -> AutoCloseableEmbeddingProvider(
                OllamaEmbeddingProvider(
                    model = model,
                    baseUrl = ollamaUrl,
                ),
            )
            EmbeddingBackend.Onnx -> AutoCloseableEmbeddingProvider(
                createOnnxEmbeddingProvider(embeddingModelDirectory),
            )
        }

    companion object {
        fun parse(args: Array<String>): Result<CliOptions> =
            runCatching {
                val values = args.toList().toOptionMap()
                val input = values["--input"] ?: error("не передан --input.")
                val output = values["--output"] ?: error("не передан --output.")
                val chunkSize = values["--chunk-size"]?.toIntOrNull() ?: 500
                val overlap = values["--overlap"]?.toIntOrNull() ?: 50
                CliOptions(
                    input = input,
                    output = output,
                    strategy = StrategyOption.from(values["--strategy"] ?: "both"),
                    chunkSize = chunkSize,
                    overlap = overlap,
                    model = values["--model"] ?: "nomic-embed-text",
                    ollamaUrl = values["--ollama-url"] ?: "http://localhost:11434",
                    embeddingBackend = EmbeddingBackend.from(values["--embedding-backend"] ?: "ollama"),
                    embeddingModelDirectory = values["--embedding-model-dir"] ?: "rag/models/nomic-embed-text",
                )
            }

        private fun List<String>.toOptionMap(): Map<String, String> {
            val result = mutableMapOf<String, String>()
            var index = 0
            while (index < size) {
                val key = this[index]
                if (!key.startsWith("--")) {
                    error("ожидался аргумент вида --name, получено '$key'.")
                }
                val value = getOrNull(index + 1) ?: error("для $key не передано значение.")
                if (value.startsWith("--")) {
                    error("для $key не передано значение.")
                }
                result[key] = value
                index += 2
            }
            return result
        }
    }
}

private class AutoCloseableEmbeddingProvider(
    private val delegate: EmbeddingProvider,
) : EmbeddingProvider,
    AutoCloseable {
    override suspend fun embed(text: String): FloatArray =
        delegate.embed(text)

    override fun close() {
        (delegate as? AutoCloseable)?.close()
    }
}

@Suppress("UNCHECKED_CAST")
private fun createOnnxEmbeddingProvider(modelDirectory: String): EmbeddingProvider {
    val clazz = Class.forName("com.sibgear.rag.data.embedding.OnnxNomicEmbeddingProvider")
    val constructor = clazz.getConstructor(String::class.java)
    return constructor.newInstance(modelDirectory) as EmbeddingProvider
}

private enum class EmbeddingBackend {
    Ollama,
    Onnx,
    ;

    companion object {
        fun from(raw: String): EmbeddingBackend =
            when (raw.lowercase()) {
                "ollama" -> Ollama
                "onnx" -> Onnx
                else -> error("--embedding-backend должен быть ollama или onnx.")
            }
    }
}

private enum class StrategyOption {
    Fixed,
    Structure,
    Both,
    ;

    fun toStrategies(): List<ChunkingStrategyType> =
        when (this) {
            Fixed -> listOf(ChunkingStrategyType.Fixed)
            Structure -> listOf(ChunkingStrategyType.Structure)
            Both -> listOf(ChunkingStrategyType.Fixed, ChunkingStrategyType.Structure)
        }

    companion object {
        fun from(raw: String): StrategyOption =
            when (raw.lowercase()) {
                "fixed" -> Fixed
                "structure" -> Structure
                "both" -> Both
                else -> error("--strategy должен быть fixed, structure или both.")
            }
    }
}
