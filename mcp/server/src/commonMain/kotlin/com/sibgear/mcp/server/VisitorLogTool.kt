package com.sibgear.mcp.server

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val MaxVisitLimit = 100
private const val VisitorLogToolName = "visitor_log"

internal data class VisitorLogInput(
    val userName: String,
    val localTime: String,
    val city: String,
    val limit: Int,
)

internal fun Server.registerVisitorLogTool(
    visitorLogRepository: VisitorLogRepository,
    weatherClient: WeatherClient,
) {
    addTool(
        name = VisitorLogToolName,
        description = "Stores the current visitor and returns recent visit log entries.",
        inputSchema = visitorLogToolSchema(),
    ) { request ->
        val arguments = request.arguments
            ?: throw IllegalArgumentException("Missing visitor_log arguments")
        val input = VisitorLogInput(
            userName = arguments.requiredString("userName"),
            localTime = arguments.requiredString("localTime"),
            city = arguments.requiredString("city"),
            limit = arguments.requiredInt("limit"),
        )
        val clientInfo = sessions[sessionId]?.clientVersion
        val visit = VisitorLogEntry(
            userName = input.userName,
            localTime = input.localTime,
            city = input.city,
            clientName = clientInfo?.name ?: "unknown",
            clientVersion = clientInfo?.version ?: "unknown",
            createdAt = currentTimestamp(),
        )
        val recentVisits = visitorLogRepository.addAndGetRecent(
            entry = visit,
            limit = input.limit.coerceIn(0, MaxVisitLimit),
        )
        val weather = weatherClient.getTemperature(input.city)
        val responseLines = recentVisits.map { it.formatForResponse() } +
            weather.formatForResponse(input.city)

        CallToolResult(
            content = listOf(TextContent(text = responseLines.joinToString("\n"))),
        )
    }
}

private fun WeatherResult.formatForResponse(city: String): String =
    when (this) {
        is WeatherResult.Available -> "Погода в $city: $temperatureCelsius °C"
        WeatherResult.Unavailable -> "Погода в $city: недоступна"
    }

private fun visitorLogToolSchema(): ToolSchema =
    ToolSchema(
        properties = buildJsonObject {
            put("userName", stringProperty("Visitor name."))
            put("localTime", stringProperty("Visitor local time."))
            put("city", stringProperty("Visitor city."))
            put(
                "limit",
                buildJsonObject {
                    put("type", "integer")
                    put("description", "How many recent visits to return.")
                    put("minimum", 0)
                    put("maximum", MaxVisitLimit)
                },
            )
        },
        required = listOf("userName", "localTime", "city", "limit"),
    )

private fun stringProperty(description: String): JsonObject =
    buildJsonObject {
        put("type", "string")
        put("description", description)
    }

private fun JsonObject.requiredString(key: String): String =
    requiredPrimitive(key).contentOrNull?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("Missing or blank '$key'")

private fun JsonObject.requiredInt(key: String): Int =
    requiredPrimitive(key).intOrNull
        ?: throw IllegalArgumentException("Missing or invalid integer '$key'")

private fun JsonObject.requiredPrimitive(key: String): JsonPrimitive =
    get(key)?.jsonPrimitive
        ?: throw IllegalArgumentException("Missing '$key'")
