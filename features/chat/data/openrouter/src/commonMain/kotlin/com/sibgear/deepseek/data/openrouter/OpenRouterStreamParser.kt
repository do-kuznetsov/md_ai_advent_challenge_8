package com.sibgear.deepseek.data.openrouter

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal class OpenRouterStreamAccumulator(
    private val json: Json,
) {
    private val pendingDataLines = mutableListOf<String>()
    private val content = StringBuilder()
    private var usage: OpenRouterResponseUsage? = null
    private var errorMessage: String? = null
    private var isDone = false

    fun acceptLine(line: String) {
        if (isDone) {
            return
        }

        if (line.isBlank()) {
            flushEvent()
            return
        }

        if (line.startsWith("data:")) {
            pendingDataLines += line.removePrefix("data:").trimStart()
        }
    }

    fun result(): OpenRouterStreamResult {
        flushEvent()

        val error = errorMessage
        val text = when {
            error == null -> content.toString()
            content.isBlank() -> "Ошибка OpenRouter stream: $error"
            else -> "${content}\n\nОшибка OpenRouter stream: $error"
        }

        return OpenRouterStreamResult(
            content = text,
            usage = usage,
            hasStreamError = error != null,
        )
    }

    private fun flushEvent() {
        if (pendingDataLines.isEmpty()) {
            return
        }

        val data = pendingDataLines.joinToString(separator = "\n")
        pendingDataLines.clear()

        if (data == "[DONE]") {
            isDone = true
            return
        }

        val chunk = runCatching {
            json.decodeFromString<OpenRouterStreamChunk>(data)
        }.getOrNull() ?: return

        chunk.error?.message?.takeIf { it.isNotBlank() }?.let { message ->
            errorMessage = message
        }
        chunk.choices.forEach { choice ->
            choice.delta?.content?.let(content::append)
        }
        chunk.usage?.let { usage = it }
    }
}

internal data class OpenRouterStreamResult(
    val content: String,
    val usage: OpenRouterResponseUsage?,
    val hasStreamError: Boolean,
)

@Serializable
private data class OpenRouterStreamChunk(
    val choices: List<OpenRouterStreamChoice> = emptyList(),
    val usage: OpenRouterResponseUsage? = null,
    val error: OpenRouterStreamError? = null,
)

@Serializable
private data class OpenRouterStreamChoice(
    val delta: OpenRouterStreamDelta? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null,
)

@Serializable
private data class OpenRouterStreamDelta(
    val content: String? = null,
)

@Serializable
private data class OpenRouterStreamError(
    val message: String? = null,
)
