package com.sibgear.deepseek.assistant.memory.domain.repository

import com.sibgear.deepseek.assistant.memory.domain.model.AssistantInvariant
import com.sibgear.deepseek.assistant.memory.domain.model.MemoryItem
import com.sibgear.deepseek.assistant.memory.domain.model.MemoryUpdate
import com.sibgear.deepseek.assistant.memory.domain.model.UserProfile

interface AssistantMemoryRepository {
    suspend fun getItems(): List<MemoryItem>
    suspend fun replaceItems(items: List<MemoryItem>): List<MemoryItem>
    suspend fun applyUpdates(updates: List<MemoryUpdate>): List<MemoryItem>
    suspend fun getProfile(): UserProfile
    suspend fun saveProfile(profile: UserProfile): UserProfile
    suspend fun getInvariants(): List<AssistantInvariant>
    suspend fun replaceInvariants(invariants: List<AssistantInvariant>): List<AssistantInvariant>
    suspend fun clear()
}
