package com.sibgear.deepseek.assistant.memory.domain.model

data class MemoryItem(
    val id: String,
    val layer: MemoryLayer,
    val fact: String,
    val importance: Double,
)

enum class MemoryLayer {
    ShortTerm,
    WorkingMemory,
    LongTermMemory,
}

data class MemoryUpdate(
    val action: MemoryUpdateAction,
    val id: String? = null,
    val layer: MemoryLayer? = null,
    val fact: String? = null,
    val importance: Double? = null,
)

enum class MemoryUpdateAction {
    Add,
    Update,
    Delete,
}

data class MemoryRetrievalPlan(
    val needShortTerm: Boolean = true,
    val needWorkingMemory: Boolean = false,
    val needLongTermMemory: Boolean = false,
    val memoryItemIds: List<String> = emptyList(),
    val reason: String = "",
)
