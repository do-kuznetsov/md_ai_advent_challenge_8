package com.sibgear.deepseek.chat.data.openrouter.internal.mapper

import com.sibgear.deepseek.chat.domain.model.StickyFact
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

internal fun String.mergeStickyFacts(currentFacts: List<StickyFact>, json: Json): List<StickyFact>? {
    val objectText = trimJsonObjectText() ?: return null
    val patch = runCatching {
        json.parseToJsonElement(objectText).jsonObject
    }.getOrNull() ?: return null

    val factsByKey = currentFacts.associateBy { it.key }.toMutableMap()
    patch.forEach { (rawKey, element) ->
        val key = rawKey.trim()
        if (key.isEmpty()) {
            return@forEach
        }

        if (element == JsonNull) {
            factsByKey.remove(key)
            return@forEach
        }

        val value = when (element) {
            is JsonPrimitive -> element.content
            is JsonObject -> element.toString()
            else -> element.toString()
        }.trim()

        if (value.isEmpty()) {
            factsByKey.remove(key)
        } else {
            factsByKey[key] = StickyFact(key = key, value = value)
        }
    }

    return factsByKey.values.sortedBy { it.key.lowercase() }
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
