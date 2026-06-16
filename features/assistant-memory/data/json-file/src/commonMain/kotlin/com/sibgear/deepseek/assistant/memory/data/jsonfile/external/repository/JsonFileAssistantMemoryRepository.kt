package com.sibgear.deepseek.assistant.memory.data.jsonfile.external.repository

import com.sibgear.deepseek.assistant.memory.data.jsonfile.internal.mapper.toAssistantMemoryFileDto
import com.sibgear.deepseek.assistant.memory.data.jsonfile.internal.mapper.toMemoryItems
import com.sibgear.deepseek.assistant.memory.data.jsonfile.internal.model.AssistantMemoryFileDto
import com.sibgear.deepseek.assistant.memory.domain.model.MemoryItem
import com.sibgear.deepseek.assistant.memory.domain.model.MemoryLayer
import com.sibgear.deepseek.assistant.memory.domain.model.MemoryUpdate
import com.sibgear.deepseek.assistant.memory.domain.model.MemoryUpdateAction
import com.sibgear.deepseek.assistant.memory.domain.repository.AssistantMemoryRepository
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class JsonFileAssistantMemoryRepository(
    private val file: File,
) : AssistantMemoryRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        prettyPrint = true
    }

    override suspend fun getItems(): List<MemoryItem> =
        readItems()

    override suspend fun replaceItems(items: List<MemoryItem>): List<MemoryItem> {
        val safeItems = items.sanitized()
        writeItems(safeItems)
        return safeItems
    }

    override suspend fun applyUpdates(updates: List<MemoryUpdate>): List<MemoryItem> {
        val current = readItems().toMutableList()
        updates.forEach { update ->
            when (update.action) {
                MemoryUpdateAction.Add -> {
                    val layer = update.layer?.takeIf { it != MemoryLayer.ShortTerm } ?: return@forEach
                    val fact = update.fact?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
                    current += MemoryItem(
                        id = update.id?.trim()?.takeIf { it.isNotEmpty() && current.none { item -> item.id == it } }
                            ?: current.nextMemoryId(),
                        layer = layer,
                        fact = fact,
                        importance = update.importance?.coerceIn(0.0, 1.0) ?: DefaultImportance,
                    )
                }

                MemoryUpdateAction.Update -> {
                    val id = update.id?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
                    val index = current.indexOfFirst { it.id == id }
                    if (index >= 0) {
                        val existing = current[index]
                        val layer = update.layer?.takeIf { it != MemoryLayer.ShortTerm } ?: existing.layer
                        val fact = update.fact?.trim()?.takeIf { it.isNotEmpty() } ?: existing.fact
                        current[index] = existing.copy(
                            layer = layer,
                            fact = fact,
                            importance = update.importance?.coerceIn(0.0, 1.0) ?: existing.importance,
                        )
                    }
                }

                MemoryUpdateAction.Delete -> {
                    val id = update.id?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
                    current.removeAll { it.id == id }
                }
            }
        }

        return replaceItems(current)
    }

    override suspend fun clear() {
        writeItems(emptyList())
    }

    private fun readItems(): List<MemoryItem> {
        if (!file.exists()) {
            return emptyList()
        }

        return runCatching {
            json.decodeFromString<AssistantMemoryFileDto>(file.readText()).toMemoryItems()
        }.getOrElse {
            preserveCorruptFile()
            emptyList()
        }
    }

    private fun writeItems(items: List<MemoryItem>) {
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }

        val tempFile = File(parent ?: File("."), "${file.name}.tmp")
        tempFile.writeText(json.encodeToString(items.toAssistantMemoryFileDto()))
        if (file.exists() && !file.delete()) {
            tempFile.delete()
            error("Cannot replace assistant memory file: ${file.absolutePath}")
        }
        if (!tempFile.renameTo(file)) {
            tempFile.copyTo(file, overwrite = true)
            tempFile.delete()
        }
    }

    private fun preserveCorruptFile() {
        runCatching {
            file.copyTo(File(file.parentFile ?: File("."), "${file.name}.corrupt"), overwrite = true)
        }
    }
}

private fun List<MemoryItem>.sanitized(): List<MemoryItem> =
    filter { item ->
        item.id.isNotBlank() &&
            item.fact.isNotBlank() &&
            item.layer != MemoryLayer.ShortTerm
    }.distinctBy { it.id }
        .map { it.copy(fact = it.fact.trim(), importance = it.importance.coerceIn(0.0, 1.0)) }

private fun List<MemoryItem>.nextMemoryId(): String {
    val next = mapNotNull { item ->
        item.id.removePrefix(MemoryIdPrefix).toIntOrNull()
    }.maxOrNull()
        ?.plus(1)
        ?: 1
    return "$MemoryIdPrefix$next"
}

private const val MemoryIdPrefix = "memory-"
private const val DefaultImportance = 0.5
