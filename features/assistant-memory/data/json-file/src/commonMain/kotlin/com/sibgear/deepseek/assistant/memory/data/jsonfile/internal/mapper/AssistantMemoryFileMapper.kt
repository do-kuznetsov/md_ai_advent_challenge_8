package com.sibgear.deepseek.assistant.memory.data.jsonfile.internal.mapper

import com.sibgear.deepseek.assistant.memory.data.jsonfile.internal.model.AssistantMemoryFileDto
import com.sibgear.deepseek.assistant.memory.data.jsonfile.internal.model.AssistantMemoryFileVersion
import com.sibgear.deepseek.assistant.memory.data.jsonfile.internal.model.MemoryItemDto
import com.sibgear.deepseek.assistant.memory.domain.model.MemoryItem
import com.sibgear.deepseek.assistant.memory.domain.model.MemoryLayer

internal fun AssistantMemoryFileDto.toMemoryItems(): List<MemoryItem> =
    items.mapNotNull { it.toDomain() }

internal fun List<MemoryItem>.toAssistantMemoryFileDto(): AssistantMemoryFileDto =
    AssistantMemoryFileDto(
        version = AssistantMemoryFileVersion,
        items = map { it.toDto() },
    )

private fun MemoryItem.toDto(): MemoryItemDto =
    MemoryItemDto(
        id = id,
        layer = layer.storageValue,
        fact = fact,
        importance = importance,
    )

private fun MemoryItemDto.toDomain(): MemoryItem? {
    val trimmedId = id.trim()
    val trimmedFact = fact.trim()
    if (trimmedId.isEmpty() || trimmedFact.isEmpty()) {
        return null
    }

    return MemoryItem(
        id = trimmedId,
        layer = layer.toMemoryLayer() ?: return null,
        fact = trimmedFact,
        importance = importance.coerceIn(0.0, 1.0),
    )
}

internal val MemoryLayer.storageValue: String
    get() = when (this) {
        MemoryLayer.ShortTerm -> "short_term"
        MemoryLayer.WorkingMemory -> "working_memory"
        MemoryLayer.LongTermMemory -> "long_term_memory"
    }

internal fun String.toMemoryLayer(): MemoryLayer? =
    when (this) {
        "short_term" -> MemoryLayer.ShortTerm
        "working_memory" -> MemoryLayer.WorkingMemory
        "long_term_memory" -> MemoryLayer.LongTermMemory
        else -> null
    }
