package com.sibgear.deepseek.chat.data.openrouter.internal.model

import kotlin.test.Test
import kotlin.test.assertEquals

class OpenRouterResponseUsageTest {
    @Test
    fun keepsZeroCostForFreeModels() {
        val usage = OpenRouterResponseUsage(cost = 0.0)

        assertEquals(
            expected = 0.0,
            actual = usage.cost,
        )
    }
}
