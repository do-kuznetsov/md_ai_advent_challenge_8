package com.sibgear.mcp.server.weather

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val GetWeatherToolName = "get_weather"

internal fun Server.registerWeatherTools(
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

private fun WeatherResult.formatForResponse(city: String): String =
    when (this) {
        is WeatherResult.Available -> "Погода в $city: $temperatureCelsius °C"
        WeatherResult.Unavailable -> "Погода в $city: недоступна"
    }

private fun getWeatherToolSchema(): ToolSchema =
    ToolSchema(
        properties = buildJsonObject {
            put("city", stringProperty("City name."))
        },
        required = listOf("city"),
    )

private fun stringProperty(description: String): JsonObject =
    buildJsonObject {
        put("type", "string")
        put("description", description)
    }

private fun JsonObject.requiredString(key: String): String =
    requiredPrimitive(key).contentOrNull?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("Missing or blank '$key'")

private fun JsonObject.requiredPrimitive(key: String): JsonPrimitive =
    get(key)?.jsonPrimitive
        ?: throw IllegalArgumentException("Missing '$key'")
