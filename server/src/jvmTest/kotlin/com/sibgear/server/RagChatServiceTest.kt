package com.sibgear.server

import com.sibgear.server.protocol.ServerRagSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class RagChatServiceTest {
    @Test
    fun normalizedRagSettingsClampInvalidValues() {
        val settings = ServerRagSettings(
            topKBeforeFilter = 1,
            topKAfterFilter = 8,
            similarityThreshold = 2f,
        ).normalized()

        assertEquals(8, settings.topKBeforeFilter)
        assertEquals(8, settings.topKAfterFilter)
        assertEquals(1f, settings.similarityThreshold)
    }

    @Test
    fun normalizedRagSettingsUseDefaultsForNonPositiveTopK() {
        val settings = ServerRagSettings(
            topKBeforeFilter = 0,
            topKAfterFilter = -1,
            similarityThreshold = -1f,
        ).normalized()

        assertEquals(15, settings.topKBeforeFilter)
        assertEquals(5, settings.topKAfterFilter)
        assertEquals(0f, settings.similarityThreshold)
    }
}
