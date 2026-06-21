package com.sibgear.deepseek.assistant.memory.data.jsonfile.internal.model

import kotlinx.serialization.Serializable

internal const val AssistantMemoryFileVersion = 1

@Serializable
internal data class AssistantMemoryFileDto(
    val version: Int = AssistantMemoryFileVersion,
    val items: List<MemoryItemDto> = emptyList(),
    val profile: UserProfileDto? = null,
    val invariants: List<AssistantInvariantDto> = emptyList(),
)

@Serializable
internal data class MemoryItemDto(
    val id: String,
    val layer: String,
    val fact: String,
    val importance: Double,
)

@Serializable
internal data class UserProfileDto(
    val text: String = "",
)

@Serializable
internal data class AssistantInvariantDto(
    val id: String,
    val category: String,
    val statement: String,
    val rationale: String = "",
    val enabled: Boolean = true,
)
