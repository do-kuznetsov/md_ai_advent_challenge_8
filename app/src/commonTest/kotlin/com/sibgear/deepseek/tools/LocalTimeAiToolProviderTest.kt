package com.sibgear.deepseek.tools

import com.sibgear.deepseek.chat.domain.model.AiToolInvocation
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalTimeAiToolProviderTest {
    @Test
    fun catalogContainsClientLocalTimeTool() = runBlocking {
        val provider = testProvider()

        provider.withSession { session ->
            assertEquals(
                listOf("get_client_local_time"),
                session.catalog.tools.map { it.name },
            )
        }
    }

    @Test
    fun returnsFormattedClientLocalTimeWithoutArguments() = runBlocking {
        val provider = testProvider(
            instant = Instant.parse("2026-06-27T12:00:00Z"),
            zoneId = ZoneId.of("Asia/Novosibirsk"),
        )

        val result = provider.callTool(
            AiToolInvocation(
                name = "get_client_local_time",
                arguments = JsonObject(emptyMap()),
            ),
        )

        assertFalse(result.isError)
        assertEquals(
            "Локальное время клиента: 2026-06-27 19:00:00 (Asia/Novosibirsk, UTC+07:00)",
            result.content,
        )
    }

    @Test
    fun formatsUtcOffsetForUtcZone() = runBlocking {
        val provider = testProvider(
            instant = Instant.parse("2026-06-27T12:00:00Z"),
            zoneId = ZoneId.of("UTC"),
        )

        val result = provider.callTool(
            AiToolInvocation(
                name = "get_client_local_time",
                arguments = JsonObject(emptyMap()),
            ),
        )

        assertFalse(result.isError)
        assertEquals(
            "Локальное время клиента: 2026-06-27 12:00:00 (UTC, UTC+00:00)",
            result.content,
        )
    }

    @Test
    fun returnsErrorForUnknownTool() = runBlocking {
        val provider = testProvider()

        val result = provider.callTool(
            AiToolInvocation(
                name = "unknown_tool",
                arguments = JsonObject(emptyMap()),
            ),
        )

        assertTrue(result.isError)
        assertEquals("Tool 'unknown_tool' не найден.", result.content)
    }

    private fun testProvider(
        instant: Instant = Instant.parse("2026-06-27T12:00:00Z"),
        zoneId: ZoneId = ZoneId.of("Asia/Novosibirsk"),
    ): LocalTimeAiToolProvider =
        LocalTimeAiToolProvider(
            clock = Clock.fixed(instant, zoneId),
            zoneIdProvider = { zoneId },
        )
}
