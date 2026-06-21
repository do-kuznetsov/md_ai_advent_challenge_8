package com.sibgear.deepseek.assistant.memory.data.jsonfile.external.repository

import com.sibgear.deepseek.assistant.memory.domain.model.AssistantInvariant
import com.sibgear.deepseek.assistant.memory.domain.model.InvariantCategory
import com.sibgear.deepseek.assistant.memory.domain.model.MemoryItem
import com.sibgear.deepseek.assistant.memory.domain.model.MemoryLayer
import com.sibgear.deepseek.assistant.memory.domain.model.MemoryUpdate
import com.sibgear.deepseek.assistant.memory.domain.model.MemoryUpdateAction
import com.sibgear.deepseek.assistant.memory.domain.model.UserProfile
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

    @Test
    fun storesProfileAlongsideMemoryItems() = runTest {
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
        repository.saveProfile(UserProfile(text = "Стиль: кратко"))

        val restored = JsonFileAssistantMemoryRepository(file)
        assertEquals(items, restored.getItems())
        assertEquals(UserProfile(text = "Стиль: кратко"), restored.getProfile())
    }

    @Test
    fun oldFileWithoutProfileRestoresEmptyProfile() = runTest {
        val file = tempMemoryFile()
        file.parentFile.mkdirs()
        file.writeText("""{"version":1,"items":[]}""")

        assertEquals(UserProfile(), JsonFileAssistantMemoryRepository(file).getProfile())
    }

    @Test
    fun storesAndRestoresInvariants() = runTest {
        val file = tempMemoryFile()
        val repository = JsonFileAssistantMemoryRepository(file)
        val invariants = listOf(
            AssistantInvariant(
                id = "invariant-1",
                category = InvariantCategory.Architecture,
                statement = "Use layered architecture",
                rationale = "Already accepted",
            ),
            AssistantInvariant(
                id = "invariant-2",
                category = InvariantCategory.StackConstraint,
                statement = "Do not add new UI frameworks",
                enabled = false,
            ),
        )

        repository.replaceInvariants(invariants)

        assertEquals(invariants, JsonFileAssistantMemoryRepository(file).getInvariants())
    }

    @Test
    fun oldFileWithoutInvariantsRestoresEmptyInvariants() = runTest {
        val file = tempMemoryFile()
        file.parentFile.mkdirs()
        file.writeText("""{"version":1,"items":[]}""")

        assertEquals(emptyList(), JsonFileAssistantMemoryRepository(file).getInvariants())
    }

    private fun tempMemoryFile(): File =
        Files.createTempDirectory("assistant-memory-json-test")
            .resolve("assistant-memory.json")
            .toFile()
}
