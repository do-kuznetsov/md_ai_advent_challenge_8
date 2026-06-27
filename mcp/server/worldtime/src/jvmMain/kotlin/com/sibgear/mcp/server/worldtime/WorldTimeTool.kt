package com.sibgear.mcp.server.worldtime

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

private const val GetWorldTimeToolName = "get_world_time"

internal fun Server.registerWorldTimeTools(
    worldTimeClient: WorldTimeClient,
) {
    addTool(
        name = GetWorldTimeToolName,
        description = "Returns current local time for a city.",
        inputSchema = getWorldTimeToolSchema(),
    ) { request ->
        val arguments = request.arguments
            ?: throw IllegalArgumentException("Missing get_world_time arguments")
        val city = arguments.requiredString("city")
        val time = worldTimeClient.getCurrentTime(city)

        CallToolResult(
            content = listOf(TextContent(text = time.formatForResponse(city))),
        )
    }
}

private fun WorldTimeResult.formatForResponse(city: String): String =
    when (this) {
        is WorldTimeResult.Available -> "Время в $city: $localDateTime ($timezone)"
        WorldTimeResult.Unavailable -> "Время в $city: недоступно"
    }

private fun getWorldTimeToolSchema(): ToolSchema =
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
    requiredPrimitive(key).contentOrNull
        ?: throw IllegalArgumentException("Missing '$key'")

private fun JsonObject.requiredPrimitive(key: String): JsonPrimitive =
    get(key)?.jsonPrimitive
        ?: throw IllegalArgumentException("Missing '$key'")
