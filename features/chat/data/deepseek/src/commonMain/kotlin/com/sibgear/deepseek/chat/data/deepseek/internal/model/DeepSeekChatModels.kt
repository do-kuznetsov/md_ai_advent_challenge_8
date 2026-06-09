package com.sibgear.deepseek.chat.data.deepseek.internal.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
)

@Serializable
internal data class DeepSeekApiChatMessage(
    val role: String,
    val content: String,
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
)

@Serializable
internal data class DeepSeekApiErrorResponse(
    val error: DeepSeekApiError? = null,
)

@Serializable
internal data class DeepSeekApiError(
    val message: String? = null,
)
