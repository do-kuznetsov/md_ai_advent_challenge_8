package com.sibgear.deepseek.assistant.memory.data.jsonfile.external.repository

import com.sibgear.deepseek.assistant.memory.domain.model.MemoryItem
import com.sibgear.deepseek.assistant.memory.domain.model.MemoryLayer
import com.sibgear.deepseek.assistant.memory.domain.model.MemoryUpdate
import com.sibgear.deepseek.assistant.memory.domain.model.MemoryUpdateAction
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonFileAssistantMemoryRepositoryTest {
    @Test
    fun storesAndRestoresMemoryItems() = runTest {
        val file = tempMemoryFile()
        val repository = JsonFileAssistantMemoryRepository(file)
        val items = listOf(
            MemoryItem(
                id = "memory-1",
                layer = MemoryLayer.WorkingMemory,
                fact = "Project uses Kotlin",
                importance = 0.8,
            ),
        )

        repository.replaceItems(items)

        assertEquals(items, JsonFileAssistantMemoryRepository(file).getItems())
    }

    @Test
    fun appliesAddUpdateAndDelete() = runTest {
        val repository = JsonFileAssistantMemoryRepository(tempMemoryFile())

        repository.applyUpdates(
            listOf(
                MemoryUpdate(
                    action = MemoryUpdateAction.Add,
                    layer = MemoryLayer.WorkingMemory,
                    fact = "Use Compose",
                    importance = 0.7,
                ),
            ),
        )
        repository.applyUpdates(
            listOf(
                MemoryUpdate(
                    action = MemoryUpdateAction.Update,
                    id = "memory-1",
                    layer = MemoryLayer.LongTermMemory,
                    fact = "User prefers concise UI",
                    importance = 0.9,
                ),
            ),
        )

        assertEquals(
            listOf(
                MemoryItem(
                    id = "memory-1",
                    layer = MemoryLayer.LongTermMemory,
                    fact = "User prefers concise UI",
                    importance = 0.9,
                ),
            ),
            repository.getItems(),
        )

        repository.applyUpdates(listOf(MemoryUpdate(action = MemoryUpdateAction.Delete, id = "memory-1")))

        assertEquals(emptyList(), repository.getItems())
    }

    private fun tempMemoryFile(): File =
        Files.createTempDirectory("assistant-memory-json-test")
            .resolve("assistant-memory.json")
            .toFile()
}
