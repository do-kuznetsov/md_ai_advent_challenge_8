package com.sibgear.deepseek.chat.data.openrouter.internal.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal data class OpenRouterCompletionResult(
    val content: String,
    val usage: OpenRouterResponseUsage? = null,
    val isRetryable: Boolean = false,
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
)

@Serializable
internal data class OpenRouterApiChatMessage(
    val role: String,
    val content: String,
)

@Serializable
internal data class OpenRouterApiErrorResponse(
    val error: OpenRouterApiError? = null,
)

@Serializable
internal data class OpenRouterApiError(
    val message: String? = null,
)
