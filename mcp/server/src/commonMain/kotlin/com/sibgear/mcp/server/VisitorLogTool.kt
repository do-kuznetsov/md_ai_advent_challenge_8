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
private const val AddVisitToolName = "add_visit"
private const val GetWeatherToolName = "get_weather"
private const val GetReportsToolName = "get_reports"

internal data class AddVisitInput(
    val userName: String,
    val localTime: String,
    val city: String,
)

internal fun Server.registerVisitTools(
    visitorLogRepository: VisitorLogRepository,
    weatherClient: WeatherClient,
) {
    registerAddVisitTool(visitorLogRepository)
    registerGetWeatherTool(weatherClient)
    registerGetReportsTool(visitorLogRepository)
}

private fun Server.registerAddVisitTool(
    visitorLogRepository: VisitorLogRepository,
) {
    addTool(
        name = AddVisitToolName,
        description = "Stores a visitor log entry without returning any data.",
        inputSchema = addVisitToolSchema(),
    ) { request ->
        val arguments = request.arguments
            ?: throw IllegalArgumentException("Missing add_visit arguments")
        val input = AddVisitInput(
            userName = arguments.requiredString("userName"),
            localTime = arguments.requiredString("localTime"),
            city = arguments.requiredString("city"),
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
        visitorLogRepository.add(visit)

        CallToolResult(
            content = emptyList(),
        )
    }
}

private fun Server.registerGetWeatherTool(
    weatherClient: WeatherClient,
) {
    addTool(
        name = GetWeatherToolName,
        description = "Returns current temperature for a city.",
        inputSchema = getWeatherToolSchema(),
    ) { request ->
        val arguments = request.arguments
            ?: throw IllegalArgumentException("Missing get_weather arguments")
        val city = arguments.requiredString("city")
        val weather = weatherClient.getTemperature(city)

        CallToolResult(
            content = listOf(TextContent(text = weather.formatForResponse(city))),
        )
    }
}

private fun Server.registerGetReportsTool(
    visitorLogRepository: VisitorLogRepository,
) {
    addTool(
        name = GetReportsToolName,
        description = "Returns recent visitor log entries.",
        inputSchema = getReportsToolSchema(),
    ) { request ->
        val arguments = request.arguments
            ?: throw IllegalArgumentException("Missing get_reports arguments")
        val limit = arguments.requiredInt("limit").coerceIn(0, MaxVisitLimit)
        val offset = arguments.optionalInt("offset")?.coerceAtLeast(0) ?: 0
        val total = visitorLogRepository.count()
        val recentVisits = visitorLogRepository.findRecent(
            limit = limit,
            offset = offset,
        )

        CallToolResult(
            content = listOf(
                TextContent(
                    text = formatReportsResponse(
                        total = total,
                        limit = limit,
                        offset = offset,
                        visits = recentVisits,
                    ),
                ),
            ),
        )
    }
}

private fun WeatherResult.formatForResponse(city: String): String =
    when (this) {
        is WeatherResult.Available -> "Погода в $city: $temperatureCelsius °C"
        WeatherResult.Unavailable -> "Погода в $city: недоступна"
    }

private fun formatReportsResponse(
    total: Int,
    limit: Int,
    offset: Int,
    visits: List<VisitorLogEntry>,
): String =
    buildString {
        appendLine("total: $total")
        appendLine("limit: $limit")
        appendLine("offset: $offset")
        appendLine("returned: ${visits.size}")
        appendLine("records:")
        visits.forEach { visit ->
            appendLine("- ${visit.formatForResponse()}")
        }
    }.trimEnd()

private fun addVisitToolSchema(): ToolSchema =
    ToolSchema(
        properties = buildJsonObject {
            put("userName", stringProperty("Visitor name."))
            put("localTime", stringProperty("Visitor local time."))
            put("city", stringProperty("Visitor city."))
        },
        required = listOf("userName", "localTime", "city"),
    )

private fun getWeatherToolSchema(): ToolSchema =
    ToolSchema(
        properties = buildJsonObject {
            put("city", stringProperty("City name."))
        },
        required = listOf("city"),
    )

private fun getReportsToolSchema(): ToolSchema =
    ToolSchema(
        properties = buildJsonObject {
            put(
                "limit",
                buildJsonObject {
                    put("type", "integer")
                    put("description", "Page size: how many recent visits to return.")
                    put("minimum", 0)
                    put("maximum", MaxVisitLimit)
                },
            )
            put(
                "offset",
                buildJsonObject {
                    put("type", "integer")
                    put("description", "How many recent visits to skip.")
                    put("minimum", 0)
                },
            )
        },
        required = listOf("limit"),
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

private fun JsonObject.optionalInt(key: String): Int? =
    get(key)?.jsonPrimitive?.intOrNull
        ?: if (containsKey(key)) throw IllegalArgumentException("Invalid integer '$key'") else null

private fun JsonObject.requiredPrimitive(key: String): JsonPrimitive =
    get(key)?.jsonPrimitive
        ?: throw IllegalArgumentException("Missing '$key'")
