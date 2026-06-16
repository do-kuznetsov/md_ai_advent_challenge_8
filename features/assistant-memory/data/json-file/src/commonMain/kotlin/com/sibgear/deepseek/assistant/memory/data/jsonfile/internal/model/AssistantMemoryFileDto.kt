package com.sibgear.deepseek.assistant.memory.data.jsonfile.internal.model

import kotlinx.serialization.Serializable

internal const val AssistantMemoryFileVersion = 1

@Serializable
internal data class AssistantMemoryFileDto(
    val version: Int = AssistantMemoryFileVersion,
    val items: List<MemoryItemDto> = emptyList(),
)

@Serializable
internal data class MemoryItemDto(
    val id: String,
    val layer: String,
    val fact: String,
    val importance: Double,
)
