package com.sibgear.deepseek.tools

import com.sibgear.deepseek.chat.domain.model.AiToolCatalog
import com.sibgear.deepseek.chat.domain.model.AiToolDefinition
import com.sibgear.deepseek.chat.domain.model.AiToolInvocation
import com.sibgear.deepseek.chat.domain.model.AiToolProvider
import com.sibgear.deepseek.chat.domain.model.AiToolResult
import com.sibgear.deepseek.chat.domain.model.AiToolSession
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal class LocalTimeAiToolProvider(
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zoneIdProvider: () -> ZoneId = ZoneId::systemDefault,
) : AiToolProvider {
    override suspend fun openSession(): AiToolSession =
        LocalTimeAiToolSession(
            clock = clock,
            zoneIdProvider = zoneIdProvider,
        )
}

private class LocalTimeAiToolSession(
    private val clock: Clock,
    private val zoneIdProvider: () -> ZoneId,
) : AiToolSession {
    override val catalog: AiToolCatalog =
        AiToolCatalog(
            tools = listOf(LocalTimeToolDefinition),
        )

    override suspend fun callTool(invocation: AiToolInvocation): AiToolResult {
        if (invocation.name != LocalTimeToolName) {
            return AiToolResult(
                name = invocation.name,
                content = "Tool '${invocation.name}' не найден.",
                isError = true,
            )
        }

        return runCatching {
            val zoneId = zoneIdProvider()
            val now = ZonedDateTime.now(clock.withZone(zoneId))
            val offset = now.offset.id.normalizeUtcOffset()
            AiToolResult(
                name = LocalTimeToolName,
                content = "Локальное время клиента: ${now.format(OutputFormatter)} ($zoneId, UTC$offset)",
            )
        }.getOrElse { throwable ->
            AiToolResult(
                name = LocalTimeToolName,
                content = "Ошибка получения локального времени клиента: " +
                    (throwable.message ?: throwable::class.simpleName ?: "unknown"),
                isError = true,
            )
        }
    }

    override suspend fun close() = Unit
}

private fun String.normalizeUtcOffset(): String =
    if (this == "Z") {
        "+00:00"
    } else {
        this
    }

private const val LocalTimeToolName = "get_client_local_time"

private val OutputFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

private val LocalTimeToolDefinition = AiToolDefinition(
    name = LocalTimeToolName,
    description = "Возвращает локальное время машины, на которой запущено desktop-приложение клиента.",
    parameters = JsonObject(
        mapOf(
            "type" to JsonPrimitive("object"),
            "properties" to JsonObject(emptyMap()),
            "additionalProperties" to JsonPrimitive(false),
        ),
    ),
)
