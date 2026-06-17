package com.sibgear.deepseek.assistant.memory.domain.interactor

import com.sibgear.deepseek.assistant.memory.domain.model.MemoryItem
import com.sibgear.deepseek.assistant.memory.domain.model.MemoryUpdate
import com.sibgear.deepseek.assistant.memory.domain.model.UserProfile
import com.sibgear.deepseek.assistant.memory.domain.repository.AssistantMemoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class AssistantMemoryInteractor(
    private val repository: AssistantMemoryRepository,
    private val dispatcher: CoroutineDispatcher,
) {
    suspend fun getItems(): List<MemoryItem> =
        withContext(dispatcher) {
            repository.getItems()
        }

    suspend fun replaceItems(items: List<MemoryItem>): List<MemoryItem> =
        withContext(dispatcher) {
            repository.replaceItems(items)
        }

    suspend fun applyUpdates(updates: List<MemoryUpdate>): List<MemoryItem> =
        withContext(dispatcher) {
            repository.applyUpdates(updates)
        }

    suspend fun getProfile(): UserProfile =
        withContext(dispatcher) {
            repository.getProfile()
        }

    suspend fun saveProfile(profile: UserProfile): UserProfile =
        withContext(dispatcher) {
            repository.saveProfile(profile)
        }

    suspend fun clear() {
        withContext(dispatcher) {
            repository.clear()
        }
    }
}
