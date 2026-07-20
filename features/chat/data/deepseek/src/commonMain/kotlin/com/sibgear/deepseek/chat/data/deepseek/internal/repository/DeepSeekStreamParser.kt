package com.sibgear.deepseek.chat.data.deepseek.internal.repository

import com.sibgear.deepseek.chat.data.deepseek.internal.model.DeepSeekResponseUsage
import com.sibgear.deepseek.chat.data.deepseek.internal.model.DeepSeekToolCall
import com.sibgear.deepseek.chat.data.deepseek.internal.model.DeepSeekToolCallFunction
import com.sibgear.deepseek.chat.domain.model.StreamingChatDelta
import com.sibgear.deepseek.chat.domain.model.StreamingChatDeltaType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal class DeepSeekStreamAccumulator(
    private val json: Json,
    private val onDelta: suspend (StreamingChatDelta) -> Unit,
) {
    private val pendingDataLines = mutableListOf<String>()
    private val content = StringBuilder()
    private val reasoningContent = StringBuilder()
    private val toolCallBuilders = linkedMapOf<Int, ToolCallBuilder>()
    private var usage: DeepSeekResponseUsage? = null
    private var errorMessage: String? = null
    private var isDone = false

    suspend fun acceptLine(line: String) {
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

    suspend fun result(): DeepSeekStreamResult {
        flushEvent()

        val error = errorMessage
        val text = when {
            error == null -> content.toString()
            content.isBlank() -> "Ошибка DeepSeek stream: $error"
            else -> "${content}\n\nОшибка DeepSeek stream: $error"
        }

        return DeepSeekStreamResult(
            content = text,
            reasoningContent = reasoningContent.toString(),
            toolCalls = toolCallBuilders.values.mapNotNull { it.buildOrNull() },
            usage = usage,
            hasStreamError = error != null,
        )
    }

    private suspend fun flushEvent() {
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
            json.decodeFromString<DeepSeekStreamChunk>(data)
        }.getOrNull() ?: return

        chunk.error?.message?.takeIf { it.isNotBlank() }?.let { message ->
            errorMessage = message
        }
        chunk.choices.forEach { choice ->
            val delta = choice.delta ?: return@forEach
            delta.reasoningContent?.takeIf { it.isNotEmpty() }?.let { text ->
                reasoningContent.append(text)
                onDelta(StreamingChatDelta(StreamingChatDeltaType.Thinking, text))
            }
            delta.content?.takeIf { it.isNotEmpty() }?.let { text ->
                content.append(text)
                onDelta(StreamingChatDelta(StreamingChatDeltaType.Content, text))
            }
            delta.toolCalls.forEach { toolCallDelta ->
                val builder = toolCallBuilders.getOrPut(toolCallDelta.index) { ToolCallBuilder() }
                builder.accept(toolCallDelta)
            }
        }
        chunk.usage?.let { usage = it }
    }
}

internal data class DeepSeekStreamResult(
    val content: String,
    val reasoningContent: String,
    val toolCalls: List<DeepSeekToolCall>,
    val usage: DeepSeekResponseUsage?,
    val hasStreamError: Boolean,
)

private class ToolCallBuilder {
    private var id: String = ""
    private var type: String = "function"
    private var name: String = ""
    private val arguments = StringBuilder()

    fun accept(delta: DeepSeekStreamToolCallDelta) {
        delta.id?.takeIf { it.isNotBlank() }?.let { id = it }
        delta.type?.takeIf { it.isNotBlank() }?.let { type = it }
        delta.function?.name?.takeIf { it.isNotBlank() }?.let { name = it }
        delta.function?.arguments?.let(arguments::append)
    }

    fun buildOrNull(): DeepSeekToolCall? {
        if (id.isBlank() || name.isBlank()) {
            return null
        }
        return DeepSeekToolCall(
            id = id,
            type = type,
            function = DeepSeekToolCallFunction(
                name = name,
                arguments = arguments.toString(),
            ),
        )
    }
}

@Serializable
private data class DeepSeekStreamChunk(
    val choices: List<DeepSeekStreamChoice> = emptyList(),
    val usage: DeepSeekResponseUsage? = null,
    val error: DeepSeekStreamError? = null,
)

@Serializable
private data class DeepSeekStreamChoice(
    val delta: DeepSeekStreamDelta? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null,
)

@Serializable
private data class DeepSeekStreamDelta(
    val content: String? = null,
    @SerialName("reasoning_content")
    val reasoningContent: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<DeepSeekStreamToolCallDelta> = emptyList(),
)

@Serializable
private data class DeepSeekStreamToolCallDelta(
    val index: Int,
    val id: String? = null,
    val type: String? = null,
    val function: DeepSeekStreamToolCallFunctionDelta? = null,
)

@Serializable
private data class DeepSeekStreamToolCallFunctionDelta(
    val name: String? = null,
    val arguments: String? = null,
)

@Serializable
private data class DeepSeekStreamError(
    val message: String? = null,
)
