package com.sibgear.deepseek.chat.data.openrouter.internal.mapper

import com.sibgear.deepseek.assistant.memory.domain.model.MemoryLayer
import com.sibgear.deepseek.assistant.memory.domain.model.MemoryRetrievalPlan
import com.sibgear.deepseek.assistant.memory.domain.model.MemoryUpdate
import com.sibgear.deepseek.assistant.memory.domain.model.MemoryUpdateAction
import com.sibgear.deepseek.chat.domain.model.ChatMemoryCandidate
import com.sibgear.deepseek.chat.domain.model.ChatMemoryLayer
import com.sibgear.deepseek.chat.domain.model.ChatMemoryRetrievalPlan
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal fun String.toMemoryCandidates(json: Json): List<ChatMemoryCandidate> =
    runCatching {
        val root = json.parseToJsonElement(extractJsonObject()).jsonObject
        if (root["store"]?.jsonPrimitive?.booleanOrNull != true) {
            return emptyList()
        }

        root["memory_items"]
            ?.jsonArrayOrNull()
            .orEmpty()
            .mapNotNull { element ->
                val item = element.jsonObject
                val layer = item["layer"]?.jsonPrimitive?.contentOrNull?.toChatMemoryLayer()
                    ?.takeIf { it != ChatMemoryLayer.ShortTerm }
                    ?: return@mapNotNull null
                val fact = item["fact"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                ChatMemoryCandidate(
                    layer = layer,
                    fact = fact,
                    importance = item["importance"]?.jsonPrimitive?.doubleOrNull?.coerceIn(0.0, 1.0) ?: 0.5,
                )
            }
    }.getOrDefault(emptyList())

internal fun String.toMemoryUpdates(json: Json): List<MemoryUpdate> =
    runCatching {
        json.parseToJsonElement(extractJsonObject()).jsonObject["updates"]
            ?.jsonArrayOrNull()
            .orEmpty()
            .mapNotNull { element ->
                val item = element.jsonObject
                val action = item["action"]?.jsonPrimitive?.contentOrNull?.toMemoryUpdateAction()
                    ?: return@mapNotNull null
                MemoryUpdate(
                    action = action,
                    id = item["id"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() },
                    layer = item["layer"]?.jsonPrimitive?.contentOrNull?.toMemoryLayer(),
                    fact = item["fact"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() },
                    importance = item["importance"]?.jsonPrimitive?.doubleOrNull?.coerceIn(0.0, 1.0),
                )
            }
    }.getOrDefault(emptyList())

internal fun String.toChatMemoryRetrievalPlan(json: Json): ChatMemoryRetrievalPlan? =
    runCatching {
        val root = json.parseToJsonElement(extractJsonObject()).jsonObject
        ChatMemoryRetrievalPlan(
            needShortTerm = root["need_short_term"]?.jsonPrimitive?.booleanOrNull ?: true,
            needWorkingMemory = root["need_working_memory"]?.jsonPrimitive?.booleanOrNull ?: false,
            needLongTermMemory = root["need_long_term_memory"]?.jsonPrimitive?.booleanOrNull ?: false,
            memoryItemIds = root["memory_ids"]
                ?.jsonArrayOrNull()
                .orEmpty()
                .mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotEmpty) },
            reason = root["reason"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }.getOrNull()

internal fun ChatMemoryRetrievalPlan.toMemoryRetrievalPlan(): MemoryRetrievalPlan =
    MemoryRetrievalPlan(
        needShortTerm = needShortTerm,
        needWorkingMemory = needWorkingMemory,
        needLongTermMemory = needLongTermMemory,
        memoryItemIds = memoryItemIds,
        reason = reason,
    )

private fun String.extractJsonObject(): String {
    val trimmed = trim()
    val withoutFence = if (trimmed.startsWith("```")) {
        trimmed
            .removePrefix("```json")
            .removePrefix("```")
            .substringBeforeLast("```")
            .trim()
    } else {
        trimmed
    }

    val start = withoutFence.indexOf('{')
    val end = withoutFence.lastIndexOf('}')
    return if (start >= 0 && end >= start) {
        withoutFence.substring(start, end + 1)
    } else {
        withoutFence
    }
}

private fun JsonElement.jsonArrayOrNull(): JsonArray? =
    this as? JsonArray

private fun String.toChatMemoryLayer(): ChatMemoryLayer? =
    when (this) {
        "short_term" -> ChatMemoryLayer.ShortTerm
        "working_memory" -> ChatMemoryLayer.WorkingMemory
        "long_term_memory" -> ChatMemoryLayer.LongTermMemory
        else -> null
    }

private fun String.toMemoryLayer(): MemoryLayer? =
    when (this) {
        "short_term" -> MemoryLayer.ShortTerm
        "working_memory" -> MemoryLayer.WorkingMemory
        "long_term_memory" -> MemoryLayer.LongTermMemory
        else -> null
    }

private fun String.toMemoryUpdateAction(): MemoryUpdateAction? =
    when (this) {
        "add" -> MemoryUpdateAction.Add
        "update" -> MemoryUpdateAction.Update
        "delete" -> MemoryUpdateAction.Delete
        else -> null
    }
