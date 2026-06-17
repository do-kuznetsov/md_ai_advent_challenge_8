package com.sibgear.deepseek.assistant.memory.domain.repository

import com.sibgear.deepseek.assistant.memory.domain.model.MemoryItem
import com.sibgear.deepseek.assistant.memory.domain.model.MemoryUpdate

interface AssistantMemoryRepository {
    suspend fun getItems(): List<MemoryItem>
    suspend fun replaceItems(items: List<MemoryItem>): List<MemoryItem>
    suspend fun applyUpdates(updates: List<MemoryUpdate>): List<MemoryItem>
    suspend fun clear()
}
