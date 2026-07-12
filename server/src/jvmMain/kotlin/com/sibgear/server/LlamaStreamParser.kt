package com.sibgear.server

import com.sibgear.server.protocol.ChatStreamEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal class LlamaStreamParser(
    private val json: Json = ServerJson,
) {
    private val splitter = ThinkTagSplitter()
    private val content = StringBuilder()
    private val thinking = StringBuilder()
    private var errorMessage: String? = null
    private var done = false

    fun acceptSseLine(
        line: String,
        emit: (ChatStreamEvent) -> Unit,
    ) {
        val trimmed = line.trim()
        if (!trimmed.startsWith("data:")) {
            return
        }
        val data = trimmed.removePrefix("data:").trim()
        if (data == "[DONE]") {
            done = true
            splitter.flush(::acceptSegment)
            return
        }

        val chunk = runCatching {
            json.decodeFromString<LlamaStreamChunk>(data)
        }.getOrElse {
            return
        }
        chunk.error?.message?.takeIf { it.isNotBlank() }?.let { errorMessage = it }
        chunk.choices.forEach { choice ->
            choice.delta?.reasoningContent
                ?.takeIf { it.isNotEmpty() }
                ?.let { delta ->
                    thinking.append(delta)
                    emit(ChatStreamEvent.ThinkingDelta(delta))
                }
            choice.delta?.content
                ?.takeIf { it.isNotEmpty() }
                ?.let { delta ->
                    splitter.accept(delta) { segment ->
                        acceptSegment(segment)
                        emit(segment.toEvent())
                    }
                }
        }
    }

    fun result(): LlamaCompletion {
        splitter.flush(::acceptSegment)
        val finalContent = when {
            errorMessage == null -> content.toString()
            content.isBlank() -> "llama.cpp stream error: $errorMessage"
            else -> "${content}\n\nllama.cpp stream error: $errorMessage"
        }
        return LlamaCompletion(
            content = finalContent,
            thinking = thinking.toString(),
            isError = errorMessage != null,
            isDone = done,
        )
    }

    private fun acceptSegment(segment: TextSegment) {
        when (segment) {
            is TextSegment.Content -> content.append(segment.text)
            is TextSegment.Thinking -> thinking.append(segment.text)
        }
    }
}

internal class ThinkTagSplitter {
    private var state: SplitState = SplitState.Content
    private var pending = ""

    fun accept(
        delta: String,
        emit: (TextSegment) -> Unit,
    ) {
        var text = pending + delta
        pending = ""

        while (text.isNotEmpty()) {
            val tag = when (state) {
                SplitState.Content -> OpenThinkTag
                SplitState.Thinking -> CloseThinkTag
            }
            val index = text.indexOf(tag)
            if (index >= 0) {
                emitText(text.take(index), emit)
                text = text.drop(index + tag.length)
                state = when (state) {
                    SplitState.Content -> SplitState.Thinking
                    SplitState.Thinking -> SplitState.Content
                }
            } else {
                val suffix = text.longestPossibleTagPrefix(tag)
                val ready = text.dropLast(suffix.length)
                pending = suffix
                emitText(ready, emit)
                return
            }
        }
    }

    fun flush(emit: (TextSegment) -> Unit) {
        emitText(pending, emit)
        pending = ""
    }

    private fun emitText(
        text: String,
        emit: (TextSegment) -> Unit,
    ) {
        if (text.isEmpty()) {
            return
        }
        emit(
            when (state) {
                SplitState.Content -> TextSegment.Content(text)
                SplitState.Thinking -> TextSegment.Thinking(text)
            },
        )
    }

    private fun String.longestPossibleTagPrefix(tag: String): String {
        val max = minOf(length, tag.length - 1)
        for (size in max downTo 1) {
            val suffix = takeLast(size)
            if (tag.startsWith(suffix)) {
                return suffix
            }
        }
        return ""
    }

    private enum class SplitState {
        Content,
        Thinking,
    }

    private companion object {
        const val OpenThinkTag = "<think>"
        const val CloseThinkTag = "</think>"
    }
}

internal sealed interface TextSegment {
    val text: String

    data class Content(override val text: String) : TextSegment
    data class Thinking(override val text: String) : TextSegment
}

private fun TextSegment.toEvent(): ChatStreamEvent =
    when (this) {
        is TextSegment.Content -> ChatStreamEvent.ContentDelta(text)
        is TextSegment.Thinking -> ChatStreamEvent.ThinkingDelta(text)
    }

data class LlamaCompletion(
    val content: String,
    val thinking: String = "",
    val isError: Boolean = false,
    val isDone: Boolean = true,
)

@Serializable
private data class LlamaStreamChunk(
    val choices: List<LlamaStreamChoice> = emptyList(),
    val error: LlamaStreamError? = null,
)

@Serializable
private data class LlamaStreamChoice(
    val delta: LlamaStreamDelta? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null,
)

@Serializable
private data class LlamaStreamDelta(
    val content: String? = null,
    @SerialName("reasoning_content")
    val reasoningContent: String? = null,
)

@Serializable
private data class LlamaStreamError(
    val message: String? = null,
)
