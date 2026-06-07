package com.sibgear.deepseek.data

import com.sibgear.deepseek.domain.AiModel
import com.sibgear.deepseek.domain.AiProvider
import kotlin.test.Test
import kotlin.test.assertEquals

class AiResponseUsageTest {
    @Test
    fun calculatesDeepSeekFlashCostWithCacheBreakdown() {
        val usage = AiResponseUsage(
            promptTokens = 1_000,
            completionTokens = 500,
            promptCacheHitTokens = 300,
            promptCacheMissTokens = 700,
        )

        assertEquals(
            expected = 0.00023884,
            actual = usage.deepSeekCost("deepseek-v4-flash") ?: 0.0,
            absoluteTolerance = 0.000000001,
        )
    }

    @Test
    fun treatsMissingDeepSeekCacheBreakdownAsCacheMiss() {
        val usage = AiResponseUsage(
            promptTokens = 1_000,
            completionTokens = 100,
        )

        assertEquals(
            expected = 0.000522,
            actual = usage.deepSeekCost("deepseek-v4-pro") ?: 0.0,
            absoluteTolerance = 0.000000001,
        )
    }

    @Test
    fun usesFlashPricingForLegacyDeepSeekChat() {
        val usage = AiResponseUsage(
            promptTokens = 1_000_000,
            completionTokens = 1_000_000,
        )

        assertEquals(
            expected = 0.42,
            actual = usage.deepSeekCost("deepseek-chat") ?: 0.0,
            absoluteTolerance = 0.000000001,
        )
    }

    @Test
    fun keepsZeroOpenRouterCostForFreeModels() {
        val usage = AiResponseUsage(cost = 0.0)

        assertEquals(
            expected = 0.0,
            actual = usage.costFor(AiModel(id = "free-model", provider = AiProvider.OpenRouter)),
        )
    }
}
