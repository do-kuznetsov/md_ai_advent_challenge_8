package com.sibgear.deepseek.assistant.memory.data.jsonfile.internal.mapper

import com.sibgear.deepseek.assistant.memory.data.jsonfile.internal.model.AssistantMemoryFileDto
import com.sibgear.deepseek.assistant.memory.data.jsonfile.internal.model.AssistantMemoryFileVersion
import com.sibgear.deepseek.assistant.memory.data.jsonfile.internal.model.MemoryItemDto
import com.sibgear.deepseek.assistant.memory.data.jsonfile.internal.model.UserProfileDto
import com.sibgear.deepseek.assistant.memory.domain.model.MemoryItem
import com.sibgear.deepseek.assistant.memory.domain.model.MemoryLayer
import com.sibgear.deepseek.assistant.memory.domain.model.UserProfile

internal fun AssistantMemoryFileDto.toMemoryItems(): List<MemoryItem> =
    items.mapNotNull { it.toDomain() }

internal fun AssistantMemoryFileDto.toUserProfile(): UserProfile =
    UserProfile(text = profile?.text.orEmpty())

internal fun List<MemoryItem>.toAssistantMemoryFileDto(profile: UserProfile): AssistantMemoryFileDto =
    AssistantMemoryFileDto(
        version = AssistantMemoryFileVersion,
        items = map { it.toDto() },
        profile = profile.toDto(),
    )

private fun MemoryItem.toDto(): MemoryItemDto =
    MemoryItemDto(
        id = id,
        layer = layer.storageValue,
        fact = fact,
        importance = importance,
    )

private fun UserProfile.toDto(): UserProfileDto =
    UserProfileDto(text = text)

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
