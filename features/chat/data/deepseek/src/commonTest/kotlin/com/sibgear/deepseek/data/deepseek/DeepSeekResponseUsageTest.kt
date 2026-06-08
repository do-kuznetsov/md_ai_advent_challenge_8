package com.sibgear.deepseek.data.deepseek

import kotlin.test.Test
import kotlin.test.assertEquals

class DeepSeekResponseUsageTest {
    @Test
    fun calculatesDeepSeekFlashCostWithCacheBreakdown() {
        val usage = DeepSeekResponseUsage(
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
        val usage = DeepSeekResponseUsage(
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
        val usage = DeepSeekResponseUsage(
            promptTokens = 1_000_000,
            completionTokens = 1_000_000,
        )

        assertEquals(
            expected = 0.42,
            actual = usage.deepSeekCost("deepseek-chat") ?: 0.0,
            absoluteTolerance = 0.000000001,
        )
    }

}
