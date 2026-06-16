package com.sibgear.deepseek.assistant.memory.data.sqldelight.external.repository

import com.sibgear.deepseek.assistant.memory.domain.model.MemoryItem
import com.sibgear.deepseek.assistant.memory.domain.model.MemoryLayer
import com.sibgear.deepseek.assistant.memory.domain.model.MemoryUpdate
import com.sibgear.deepseek.assistant.memory.domain.model.MemoryUpdateAction
import com.sibgear.deepseek.assistant.memory.domain.repository.AssistantMemoryRepository
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

class SqldelightAssistantMemoryRepository(
    private val databaseFile: File,
) : AssistantMemoryRepository {
    init {
        databaseFile.parentFile?.mkdirs()
        withConnection { connection ->
            connection.createStatement().use { statement ->
                statement.execute(createMemoryTableSql())
            }
        }
    }

    override suspend fun getItems(): List<MemoryItem> =
        withConnection { connection ->
            connection.prepareStatement(selectAllSql()).use { statement ->
                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            resultSet.toMemoryItem()?.let(::add)
                        }
                    }
                }
            }
        }

    override suspend fun replaceItems(items: List<MemoryItem>): List<MemoryItem> {
        val safeItems = items.sanitized()
        withConnection { connection ->
            connection.autoCommit = false
            try {
                connection.createStatement().use { statement ->
                    statement.executeUpdate(deleteAllSql())
                }
                connection.prepareStatement(insertSql()).use { statement ->
                    safeItems.forEach { item ->
                        statement.setString(1, item.id)
                        statement.setString(2, item.layer.storageValue)
                        statement.setString(3, item.fact)
                        statement.setDouble(4, item.importance)
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }
                connection.commit()
            } catch (exception: Throwable) {
                connection.rollback()
                throw exception
            } finally {
                connection.autoCommit = true
            }
        }
        return getItems()
    }

    override suspend fun applyUpdates(updates: List<MemoryUpdate>): List<MemoryItem> {
        val current = getItems().toMutableList()
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
                        current[index] = existing.copy(
                            layer = update.layer?.takeIf { it != MemoryLayer.ShortTerm } ?: existing.layer,
                            fact = update.fact?.trim()?.takeIf { it.isNotEmpty() } ?: existing.fact,
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
        withConnection { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(deleteAllSql())
            }
        }
    }

    private fun <T> withConnection(block: (Connection) -> T): T =
        DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}").use(block)
}

private fun ResultSet.toMemoryItem(): MemoryItem? {
    val id = getString("memory_id")?.trim().orEmpty()
    val fact = getString("fact")?.trim().orEmpty()
    if (id.isEmpty() || fact.isEmpty()) {
        return null
    }

    return MemoryItem(
        id = id,
        layer = getString("layer").toMemoryLayer() ?: return null,
        fact = fact,
        importance = getDouble("importance").coerceIn(0.0, 1.0),
    )
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

private val MemoryLayer.storageValue: String
    get() = when (this) {
        MemoryLayer.ShortTerm -> "short_term"
        MemoryLayer.WorkingMemory -> "working_memory"
        MemoryLayer.LongTermMemory -> "long_term_memory"
    }

private fun String?.toMemoryLayer(): MemoryLayer? =
    when (this) {
        "short_term" -> MemoryLayer.ShortTerm
        "working_memory" -> MemoryLayer.WorkingMemory
        "long_term_memory" -> MemoryLayer.LongTermMemory
        else -> null
    }

private fun createMemoryTableSql(): String =
    """
    CREATE TABLE IF NOT EXISTS $MemoryTableName (
        memory_id TEXT NOT NULL PRIMARY KEY,
        layer TEXT NOT NULL,
        fact TEXT NOT NULL,
        importance REAL NOT NULL
    )
    """.trimIndent()

private fun selectAllSql(): String =
    "SELECT * FROM $MemoryTableName ORDER BY memory_id ASC"

private fun insertSql(): String =
    """
    INSERT INTO $MemoryTableName(
        memory_id,
        layer,
        fact,
        importance
    )
    VALUES (?, ?, ?, ?)
    """.trimIndent()

private fun deleteAllSql(): String =
    "DELETE FROM $MemoryTableName"

private const val MemoryTableName = "assistant_memory_item"
private const val MemoryIdPrefix = "memory-"
private const val DefaultImportance = 0.5
