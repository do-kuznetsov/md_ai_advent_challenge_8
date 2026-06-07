package com.sibgear.deepseek.data

import com.sibgear.deepseek.domain.AiModel
import com.sibgear.deepseek.domain.AiProvider
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class AiResponseUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int? = null,
    @SerialName("completion_tokens")
    val completionTokens: Int? = null,
    @SerialName("total_tokens")
    val totalTokens: Int? = null,
    @SerialName("prompt_cache_hit_tokens")
    val promptCacheHitTokens: Int? = null,
    @SerialName("prompt_cache_miss_tokens")
    val promptCacheMissTokens: Int? = null,
    val cost: Double? = null,
) {
    val displayTotalTokens: Int?
        get() = totalTokens ?: listOfNotNull(promptTokens, completionTokens)
            .takeIf { it.isNotEmpty() }
            ?.sum()
}

internal fun AiResponseUsage.costFor(model: AiModel): Double? =
    when (model.provider) {
        AiProvider.DeepSeek -> deepSeekCost(model.id)
        AiProvider.OpenRouter -> cost
    }

internal fun AiResponseUsage.deepSeekCost(modelId: String): Double? {
    val prices = deepSeekPrices(modelId) ?: return null
    val promptTokensValue = promptTokens ?: 0
    val cacheHitTokens = promptCacheHitTokens
        ?: promptCacheMissTokens?.let { (promptTokensValue - it).coerceAtLeast(0) }
        ?: 0
    val cacheMissTokens = promptCacheMissTokens
        ?: promptCacheHitTokens?.let { (promptTokensValue - it).coerceAtLeast(0) }
        ?: promptTokensValue
    val outputTokens = completionTokens ?: 0

    return (
        cacheHitTokens * prices.inputCacheHit +
            cacheMissTokens * prices.inputCacheMiss +
            outputTokens * prices.output
        ) / TokensPerMillion
}

private fun deepSeekPrices(modelId: String): DeepSeekPrices? =
    when (modelId) {
        "deepseek-v4-flash", "deepseek-chat", "deepseek-reasoner" -> DeepSeekPrices(
            inputCacheHit = 0.0028,
            inputCacheMiss = 0.14,
            output = 0.28,
        )

        "deepseek-v4-pro" -> DeepSeekPrices(
            inputCacheHit = 0.003625,
            inputCacheMiss = 0.435,
            output = 0.87,
        )

        else -> null
    }

private data class DeepSeekPrices(
    val inputCacheHit: Double,
    val inputCacheMiss: Double,
    val output: Double,
)

private const val TokensPerMillion = 1_000_000.0
