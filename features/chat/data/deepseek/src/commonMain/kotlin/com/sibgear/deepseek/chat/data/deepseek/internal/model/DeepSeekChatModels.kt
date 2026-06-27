package com.sibgear.deepseek.chat.data.deepseek.internal.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class DeepSeekChatCompletionRequest(
    val model: String,
    val messages: List<DeepSeekApiChatMessage>,
    val stream: Boolean,
    val thinking: DeepSeekThinking? = null,
    val temperature: Float? = null,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    val stop: List<String>? = null,
    val tools: List<DeepSeekChatTool>? = null,
    @SerialName("tool_choice")
    val toolChoice: String? = null,
)

@Serializable
internal data class DeepSeekApiChatMessage(
    val role: String,
    val content: String? = null,
    @SerialName("tool_call_id")
    val toolCallId: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<DeepSeekToolCall>? = null,
)

@Serializable
internal data class DeepSeekChatTool(
    val type: String,
    val function: DeepSeekChatToolFunction,
)

@Serializable
internal data class DeepSeekChatToolFunction(
    val name: String,
    val description: String? = null,
    val parameters: JsonObject,
)

@Serializable
internal data class DeepSeekThinking(
    val type: String,
)

@Serializable
internal data class DeepSeekChatCompletionResponse(
    val choices: List<DeepSeekChoice> = emptyList(),
    val usage: DeepSeekResponseUsage? = null,
)

@Serializable
internal data class DeepSeekChoice(
    val message: DeepSeekAssistantMessage? = null,
)

@Serializable
internal data class DeepSeekAssistantMessage(
    val content: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<DeepSeekToolCall>? = null,
)

@Serializable
internal data class DeepSeekToolCall(
    val id: String,
    val type: String,
    val function: DeepSeekToolCallFunction,
)

@Serializable
internal data class DeepSeekToolCallFunction(
    val name: String,
    val arguments: String,
)

@Serializable
internal data class DeepSeekApiErrorResponse(
    val error: DeepSeekApiError? = null,
)

@Serializable
internal data class DeepSeekApiError(
    val message: String? = null,
)
