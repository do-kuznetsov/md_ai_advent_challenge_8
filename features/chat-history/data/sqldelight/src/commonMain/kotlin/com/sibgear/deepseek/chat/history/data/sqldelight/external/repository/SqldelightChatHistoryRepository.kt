package com.sibgear.deepseek.chat.history.data.sqldelight.external.repository

import com.sibgear.deepseek.chat.history.domain.model.HistoryFact
import com.sibgear.deepseek.chat.history.domain.model.HistoryBranch
import com.sibgear.deepseek.chat.history.domain.model.HistoryMemoryChange
import com.sibgear.deepseek.chat.history.domain.model.HistoryMemoryChangeAction
import com.sibgear.deepseek.chat.history.domain.model.HistoryMemoryItem
import com.sibgear.deepseek.chat.history.domain.model.HistoryMemoryLayer
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessage
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageAttachment
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageFooter
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageKind
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageMemoryMetadata
import com.sibgear.deepseek.chat.history.domain.model.HistoryRole
import com.sibgear.deepseek.chat.history.domain.repository.ChatHistoryRepository
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SqldelightChatHistoryRepository(
    private val databaseFile: File,
    chatId: Int,
) : ChatHistoryRepository {
    private val tableName = chatId.toTableName()
    private val factsTableName = chatId.toFactsTableName()
    private val branchesTableName = chatId.toBranchesTableName()
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    init {
        databaseFile.parentFile?.mkdirs()
        withConnection { connection ->
            connection.createStatement().use { statement ->
                statement.execute(createTableSql(tableName))
                statement.execute(createFactsTableSql(factsTableName))
                statement.execute(createBranchesTableSql(branchesTableName))
            }
            connection.ensureAttachmentColumns(tableName)
        }
    }

    override suspend fun add(message: HistoryMessage): List<HistoryMessage> {
        withConnection { connection ->
            connection.prepareStatement(insertSql(tableName)).use { statement ->
                statement.bind(message)
                statement.executeUpdate()
            }
        }
        return getMessages()
    }

    override suspend fun getMessages(): List<HistoryMessage> =
        withConnection { connection ->
            connection.prepareStatement(selectAllSql(tableName)).use { statement ->
                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            add(resultSet.toHistoryMessage())
                        }
                    }
                }
            }
        }

    override suspend fun replace(messages: List<HistoryMessage>): List<HistoryMessage> {
        withConnection { connection ->
            connection.autoCommit = false
            try {
                connection.createStatement().use { statement ->
                    statement.executeUpdate(deleteAllSql(tableName))
                }
                connection.prepareStatement(insertSql(tableName)).use { statement ->
                    messages.forEach { message ->
                        statement.bind(message)
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
        return getMessages()
    }

    override suspend fun getFacts(): List<HistoryFact> =
        withConnection { connection ->
            connection.prepareStatement(selectAllFactsSql(factsTableName)).use { statement ->
                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            add(
                                HistoryFact(
                                    key = resultSet.getString("fact_key"),
                                    value = resultSet.getString("fact_value"),
                                ),
                            )
                        }
                    }
                }
            }
        }

    override suspend fun replaceFacts(facts: List<HistoryFact>): List<HistoryFact> {
        withConnection { connection ->
            connection.autoCommit = false
            try {
                connection.createStatement().use { statement ->
                    statement.executeUpdate(deleteAllSql(factsTableName))
                }
                connection.prepareStatement(insertFactSql(factsTableName)).use { statement ->
                    facts.forEach { fact ->
                        statement.setString(1, fact.key)
                        statement.setString(2, fact.value)
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
        return getFacts()
    }

    override suspend fun getBranches(): List<HistoryBranch> =
        withConnection { connection ->
            connection.prepareStatement(selectAllBranchesSql(branchesTableName)).use { statement ->
                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            add(
                                HistoryBranch(
                                    id = resultSet.getLong("branch_id").toInt(),
                                    parentId = resultSet.getNullableLong("parent_branch_id")?.toInt(),
                                    title = resultSet.getString("title"),
                                    summary = resultSet.getString("summary"),
                                ),
                            )
                        }
                    }
                }
            }
        }

    override suspend fun replaceBranches(branches: List<HistoryBranch>): List<HistoryBranch> {
        withConnection { connection ->
            connection.autoCommit = false
            try {
                connection.createStatement().use { statement ->
                    statement.executeUpdate(deleteAllSql(branchesTableName))
                }
                connection.prepareStatement(insertBranchSql(branchesTableName)).use { statement ->
                    branches.forEach { branch ->
                        statement.setLong(1, branch.id.toLong())
                        branch.parentId?.let { statement.setLong(2, it.toLong()) } ?: statement.setObject(2, null)
                        statement.setString(3, branch.title)
                        statement.setString(4, branch.summary)
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
        return getBranches()
    }

    override suspend fun clear() {
        withConnection { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(deleteAllSql(tableName))
                statement.executeUpdate(deleteAllSql(factsTableName))
                statement.executeUpdate(deleteAllSql(branchesTableName))
            }
        }
    }

    private fun <T> withConnection(block: (Connection) -> T): T =
        DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}").use(block)

    private fun java.sql.PreparedStatement.bind(message: HistoryMessage) {
        setString(1, message.role.databaseValue)
        setString(2, message.content)
        setString(3, message.thinkingContent)
        setString(4, message.kind.databaseValue)
        message.branchId?.let { setLong(5, it.toLong()) } ?: setObject(5, null)
        setString(6, message.apiContent)
        message.attachment?.let { attachment ->
            setString(7, attachment.fileName)
            setLong(8, attachment.sizeBytes)
        } ?: run {
            setObject(7, null)
            setObject(8, null)
        }
        setString(9, message.memory?.toJson(json))
        setString(10, message.sourceLabel)
        message.footer?.let { footer ->
            setLong(11, footer.responseTimeMs)
            footer.promptTokens?.let { setLong(12, it.toLong()) } ?: setObject(12, null)
            footer.completionTokens?.let { setLong(13, it.toLong()) } ?: setObject(13, null)
            footer.totalTokens?.let { setLong(14, it.toLong()) } ?: setObject(14, null)
            footer.cost?.let { setDouble(15, it) } ?: setObject(15, null)
            setLong(16, footer.retryCount.toLong())
        } ?: run {
            setObject(11, null)
            setObject(12, null)
            setObject(13, null)
            setObject(14, null)
            setObject(15, null)
            setObject(16, null)
        }
    }

    private fun ResultSet.toHistoryMessage(): HistoryMessage =
        HistoryMessage(
            role = getString("role").toHistoryRole(),
            content = getString("content"),
            thinkingContent = getString("thinking_content"),
            branchId = getNullableLong("branch_id")?.toInt(),
            kind = getString("kind").toHistoryMessageKind(),
            apiContent = getString("api_content"),
            attachment = getString("attachment_file_name")?.let { fileName ->
                HistoryMessageAttachment(
                    fileName = fileName,
                    sizeBytes = getNullableLong("attachment_size_bytes") ?: 0L,
                )
            },
            memory = getString("memory_json")?.toHistoryMemoryMetadata(json),
            sourceLabel = getString("source_label"),
            footer = getNullableLong("response_time_ms")?.let { responseTimeMs ->
                HistoryMessageFooter(
                    responseTimeMs = responseTimeMs,
                    promptTokens = getNullableLong("prompt_tokens")?.toInt(),
                    completionTokens = getNullableLong("completion_tokens")?.toInt(),
                    totalTokens = getNullableLong("total_tokens")?.toInt(),
                    cost = getNullableDouble("cost"),
                    retryCount = getNullableLong("retry_count")?.toInt() ?: 0,
                )
            },
        )

    private fun ResultSet.getNullableLong(column: String): Long? {
        val value = getLong(column)
        return if (wasNull()) null else value
    }

    private fun ResultSet.getNullableDouble(column: String): Double? {
        val value = getDouble(column)
        return if (wasNull()) null else value
    }

    private val HistoryRole.databaseValue: String
        get() = when (this) {
            HistoryRole.User -> UserRole
            HistoryRole.Assistant -> AssistantRole
        }

    private val HistoryMessageKind.databaseValue: String
        get() = when (this) {
            HistoryMessageKind.Regular -> RegularKind
            HistoryMessageKind.CompressionSummary -> CompressionSummaryKind
            HistoryMessageKind.TaskStateEvent -> TaskStateEventKind
            HistoryMessageKind.RagDiagnostic -> RagDiagnosticKind
        }

    private fun String.toHistoryRole(): HistoryRole =
        when (this) {
            UserRole -> HistoryRole.User
            AssistantRole -> HistoryRole.Assistant
            else -> error("Unknown history message role: $this")
        }

    private fun String?.toHistoryMessageKind(): HistoryMessageKind =
        when (this) {
            CompressionSummaryKind -> HistoryMessageKind.CompressionSummary
            TaskStateEventKind -> HistoryMessageKind.TaskStateEvent
            RagDiagnosticKind -> HistoryMessageKind.RagDiagnostic
            else -> HistoryMessageKind.Regular
        }

    private companion object {
        const val UserRole = "user"
        const val AssistantRole = "assistant"
        const val RegularKind = "regular"
        const val CompressionSummaryKind = "compression_summary"
        const val TaskStateEventKind = "task_state_event"
        const val RagDiagnosticKind = "rag_diagnostic"
    }
}

internal fun Int.toTableName(): String {
    require(this > 0) { "chatId must be positive" }
    return "history_message_tab_$this"
}

internal fun Int.toFactsTableName(): String {
    require(this > 0) { "chatId must be positive" }
    return "history_fact_tab_$this"
}

internal fun Int.toBranchesTableName(): String {
    require(this > 0) { "chatId must be positive" }
    return "history_branch_tab_$this"
}

internal fun createTableSql(tableName: String): String =
    """
    CREATE TABLE IF NOT EXISTS $tableName (
        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
        role TEXT NOT NULL,
        content TEXT NOT NULL,
        thinking_content TEXT,
        kind TEXT NOT NULL DEFAULT 'regular',
        branch_id INTEGER,
        api_content TEXT,
        attachment_file_name TEXT,
        attachment_size_bytes INTEGER,
        memory_json TEXT,
        source_label TEXT,
        response_time_ms INTEGER,
        prompt_tokens INTEGER,
        completion_tokens INTEGER,
        total_tokens INTEGER,
        cost REAL,
        retry_count INTEGER
    )
    """.trimIndent()

internal fun createFactsTableSql(tableName: String): String =
    """
    CREATE TABLE IF NOT EXISTS $tableName (
        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
        fact_key TEXT NOT NULL,
        fact_value TEXT NOT NULL
    )
    """.trimIndent()

internal fun createBranchesTableSql(tableName: String): String =
    """
    CREATE TABLE IF NOT EXISTS $tableName (
        branch_id INTEGER NOT NULL PRIMARY KEY,
        parent_branch_id INTEGER,
        title TEXT NOT NULL,
        summary TEXT NOT NULL
    )
    """.trimIndent()

internal fun insertSql(tableName: String): String =
    """
    INSERT INTO $tableName(
        role,
        content,
        thinking_content,
        kind,
        branch_id,
        api_content,
        attachment_file_name,
        attachment_size_bytes,
        memory_json,
        source_label,
        response_time_ms,
        prompt_tokens,
        completion_tokens,
        total_tokens,
        cost,
        retry_count
    )
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """.trimIndent()

internal fun selectAllSql(tableName: String): String =
    "SELECT * FROM $tableName ORDER BY id ASC"

internal fun selectAllFactsSql(tableName: String): String =
    "SELECT * FROM $tableName ORDER BY id ASC"

internal fun selectAllBranchesSql(tableName: String): String =
    "SELECT * FROM $tableName ORDER BY branch_id ASC"

internal fun insertFactSql(tableName: String): String =
    """
    INSERT INTO $tableName(
        fact_key,
        fact_value
    )
    VALUES (?, ?)
    """.trimIndent()

internal fun insertBranchSql(tableName: String): String =
    """
    INSERT INTO $tableName(
        branch_id,
        parent_branch_id,
        title,
        summary
    )
    VALUES (?, ?, ?, ?)
    """.trimIndent()

internal fun deleteAllSql(tableName: String): String =
    "DELETE FROM $tableName"

private fun Connection.ensureAttachmentColumns(tableName: String) {
    val existingColumns = createStatement().use { statement ->
        statement.executeQuery("PRAGMA table_info($tableName)").use { resultSet ->
            buildSet {
                while (resultSet.next()) {
                    add(resultSet.getString("name"))
                }
            }
        }
    }

    createStatement().use { statement ->
        MissingColumns.forEach { column ->
            if (column.name !in existingColumns) {
                statement.execute("ALTER TABLE $tableName ADD COLUMN ${column.name} ${column.type}")
            }
        }
    }
}

private data class MissingColumn(
    val name: String,
    val type: String,
)

private val MissingColumns = listOf(
    MissingColumn("kind", "TEXT DEFAULT 'regular'"),
    MissingColumn("thinking_content", "TEXT"),
    MissingColumn("branch_id", "INTEGER"),
    MissingColumn("api_content", "TEXT"),
    MissingColumn("attachment_file_name", "TEXT"),
    MissingColumn("attachment_size_bytes", "INTEGER"),
    MissingColumn("memory_json", "TEXT"),
)

@Serializable
private data class HistoryMessageMemoryDto(
    val storedLayers: List<String> = emptyList(),
    val usedLayers: List<String> = emptyList(),
    val changes: List<HistoryMemoryChangeDto> = emptyList(),
    val injectedItems: List<HistoryMemoryItemDto> = emptyList(),
    val error: String? = null,
)

@Serializable
private data class HistoryMemoryChangeDto(
    val action: String,
    val layer: String,
    val fact: String,
)

@Serializable
private data class HistoryMemoryItemDto(
    val id: String,
    val layer: String,
    val fact: String,
    val importance: Double,
)

private fun HistoryMessageMemoryMetadata.toJson(json: Json): String =
    json.encodeToString(
        HistoryMessageMemoryDto(
            storedLayers = storedLayers.map { it.storageValue },
            usedLayers = usedLayers.map { it.storageValue },
            changes = changes.map {
                HistoryMemoryChangeDto(
                    action = it.action.storageValue,
                    layer = it.layer.storageValue,
                    fact = it.fact,
                )
            },
            injectedItems = injectedItems.map {
                HistoryMemoryItemDto(
                    id = it.id,
                    layer = it.layer.storageValue,
                    fact = it.fact,
                    importance = it.importance,
                )
            },
            error = error,
        ),
    )

private fun String.toHistoryMemoryMetadata(json: Json): HistoryMessageMemoryMetadata? =
    runCatching {
        val dto = json.decodeFromString<HistoryMessageMemoryDto>(this)
        HistoryMessageMemoryMetadata(
            storedLayers = dto.storedLayers.mapNotNull { it.toHistoryMemoryLayer() },
            usedLayers = dto.usedLayers.mapNotNull { it.toHistoryMemoryLayer() },
            changes = dto.changes.mapNotNull { it.toDomain() },
            injectedItems = dto.injectedItems.mapNotNull { it.toDomain() },
            error = dto.error,
        )
    }.getOrNull()

private fun HistoryMemoryChangeDto.toDomain(): HistoryMemoryChange? {
    val trimmedFact = fact.trim()
    if (trimmedFact.isEmpty()) {
        return null
    }

    return HistoryMemoryChange(
        action = action.toHistoryMemoryChangeAction() ?: return null,
        layer = layer.toHistoryMemoryLayer() ?: return null,
        fact = trimmedFact,
    )
}

private fun HistoryMemoryItemDto.toDomain(): HistoryMemoryItem? {
    val trimmedId = id.trim()
    val trimmedFact = fact.trim()
    if (trimmedId.isEmpty() || trimmedFact.isEmpty()) {
        return null
    }

    return HistoryMemoryItem(
        id = trimmedId,
        layer = layer.toHistoryMemoryLayer() ?: return null,
        fact = trimmedFact,
        importance = importance.coerceIn(0.0, 1.0),
    )
}

private val HistoryMemoryLayer.storageValue: String
    get() = when (this) {
        HistoryMemoryLayer.ShortTerm -> "short_term"
        HistoryMemoryLayer.WorkingMemory -> "working_memory"
        HistoryMemoryLayer.LongTermMemory -> "long_term_memory"
    }

private fun String.toHistoryMemoryLayer(): HistoryMemoryLayer? =
    when (this) {
        "short_term" -> HistoryMemoryLayer.ShortTerm
        "working_memory" -> HistoryMemoryLayer.WorkingMemory
        "long_term_memory" -> HistoryMemoryLayer.LongTermMemory
        else -> null
    }

private val HistoryMemoryChangeAction.storageValue: String
    get() = when (this) {
        HistoryMemoryChangeAction.Add -> "add"
        HistoryMemoryChangeAction.Update -> "update"
        HistoryMemoryChangeAction.Delete -> "delete"
    }

private fun String.toHistoryMemoryChangeAction(): HistoryMemoryChangeAction? =
    when (this) {
        "add" -> HistoryMemoryChangeAction.Add
        "update" -> HistoryMemoryChangeAction.Update
        "delete" -> HistoryMemoryChangeAction.Delete
        else -> null
    }
