package com.sibgear.deepseek.chat.data.openrouter.internal.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class OpenRouterResponseUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int? = null,
    @SerialName("completion_tokens")
    val completionTokens: Int? = null,
    @SerialName("total_tokens")
    val totalTokens: Int? = null,
    val cost: Double? = null,
) {
    val displayTotalTokens: Int?
        get() = totalTokens ?: listOfNotNull(promptTokens, completionTokens)
            .takeIf { it.isNotEmpty() }
            ?.sum()
}
