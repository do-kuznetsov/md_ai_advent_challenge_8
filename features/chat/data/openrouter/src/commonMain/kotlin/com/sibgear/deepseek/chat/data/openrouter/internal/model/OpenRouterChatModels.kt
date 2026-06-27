package com.sibgear.deepseek.chat.data.openrouter.internal.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

internal data class OpenRouterCompletionResult(
    val content: String,
    val usage: OpenRouterResponseUsage? = null,
    val isRetryable: Boolean = false,
    val isError: Boolean = false,
)

@Serializable
internal data class OpenRouterChatCompletionRequest(
    val model: String,
    val messages: List<OpenRouterApiChatMessage>,
    val stream: Boolean,
    val temperature: Float? = null,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    val stop: List<String>? = null,
    val tools: List<OpenRouterChatTool>? = null,
    @SerialName("tool_choice")
    val toolChoice: String? = null,
)

@Serializable
internal data class OpenRouterApiChatMessage(
    val role: String,
    val content: String? = null,
    @SerialName("tool_call_id")
    val toolCallId: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<OpenRouterToolCall>? = null,
)

@Serializable
internal data class OpenRouterChatTool(
    val type: String,
    val function: OpenRouterChatToolFunction,
)

@Serializable
internal data class OpenRouterChatToolFunction(
    val name: String,
    val description: String? = null,
    val parameters: JsonObject,
)

@Serializable
internal data class OpenRouterChatCompletionResponse(
    val choices: List<OpenRouterChoice> = emptyList(),
    val usage: OpenRouterResponseUsage? = null,
)

@Serializable
internal data class OpenRouterChoice(
    val message: OpenRouterAssistantMessage? = null,
)

@Serializable
internal data class OpenRouterAssistantMessage(
    val content: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<OpenRouterToolCall>? = null,
)

@Serializable
internal data class OpenRouterToolCall(
    val id: String,
    val type: String,
    val function: OpenRouterToolCallFunction,
)

@Serializable
internal data class OpenRouterToolCallFunction(
    val name: String,
    val arguments: String,
)

@Serializable
internal data class OpenRouterApiErrorResponse(
    val error: OpenRouterApiError? = null,
)

@Serializable
internal data class OpenRouterApiError(
    val message: String? = null,
)
