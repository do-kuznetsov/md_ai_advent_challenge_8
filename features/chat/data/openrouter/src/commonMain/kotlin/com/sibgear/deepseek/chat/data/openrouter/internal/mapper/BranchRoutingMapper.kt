package com.sibgear.deepseek.chat.data.openrouter.internal.mapper

import com.sibgear.deepseek.chat.domain.model.BranchRoutingDecision
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal fun String.toBranchRoutingDecision(json: Json): BranchRoutingDecision? {
    val objectText = trimJsonObjectText() ?: return null
    val jsonObject = runCatching {
        json.parseToJsonElement(objectText).jsonObject
    }.getOrNull() ?: return null

    return when (jsonObject.stringValue("type")?.lowercase()) {
        "existing" -> jsonObject.intValue("branchId")?.let(BranchRoutingDecision::Existing)
        "new" -> BranchRoutingDecision.New(
            parentBranchId = jsonObject.intValue("parentBranchId"),
            title = jsonObject.stringValue("title").orEmpty(),
            summary = jsonObject.stringValue("summary").orEmpty(),
        )
        else -> null
    }
}

private fun String.trimJsonObjectText(): String? {
    val trimmed = trim()
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
    val start = trimmed.indexOf('{')
    val end = trimmed.lastIndexOf('}')
    if (start < 0 || end < start) {
        return null
    }

    return trimmed.substring(start, end + 1)
}

private fun JsonObject.stringValue(key: String): String? =
    get(key)
        ?.takeUnless { it == JsonNull }
        ?.let { element ->
            when (element) {
                is JsonPrimitive -> element.content
                else -> element.toString()
            }
        }
        ?.trim()

private fun JsonObject.intValue(key: String): Int? =
    get(key)
        ?.takeUnless { it == JsonNull }
        ?.jsonPrimitive
        ?.intOrNull
