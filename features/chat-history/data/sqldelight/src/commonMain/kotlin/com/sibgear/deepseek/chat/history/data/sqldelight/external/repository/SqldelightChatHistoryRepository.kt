package com.sibgear.deepseek.chat.history.data.sqldelight.external.repository

import com.sibgear.deepseek.chat.history.domain.model.HistoryMessage
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageFooter
import com.sibgear.deepseek.chat.history.domain.model.HistoryRole
import com.sibgear.deepseek.chat.history.domain.repository.ChatHistoryRepository
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

class SqldelightChatHistoryRepository(
    private val databaseFile: File,
    chatId: Int,
) : ChatHistoryRepository {
    private val tableName = chatId.toTableName()

    init {
        databaseFile.parentFile?.mkdirs()
        withConnection { connection ->
            connection.createStatement().use { statement ->
                statement.execute(createTableSql(tableName))
            }
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

    override suspend fun clear() {
        withConnection { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(deleteAllSql(tableName))
            }
        }
    }

    private fun <T> withConnection(block: (Connection) -> T): T =
        DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}").use(block)

    private fun java.sql.PreparedStatement.bind(message: HistoryMessage) {
        setString(1, message.role.databaseValue)
        setString(2, message.content)
        setString(3, message.sourceLabel)
        message.footer?.let { footer ->
            setLong(4, footer.responseTimeMs)
            footer.promptTokens?.let { setLong(5, it.toLong()) } ?: setObject(5, null)
            footer.completionTokens?.let { setLong(6, it.toLong()) } ?: setObject(6, null)
            footer.totalTokens?.let { setLong(7, it.toLong()) } ?: setObject(7, null)
            footer.cost?.let { setDouble(8, it) } ?: setObject(8, null)
            setLong(9, footer.retryCount.toLong())
        } ?: run {
            setObject(4, null)
            setObject(5, null)
            setObject(6, null)
            setObject(7, null)
            setObject(8, null)
            setObject(9, null)
        }
    }

    private fun ResultSet.toHistoryMessage(): HistoryMessage =
        HistoryMessage(
            role = getString("role").toHistoryRole(),
            content = getString("content"),
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

    private fun String.toHistoryRole(): HistoryRole =
        when (this) {
            UserRole -> HistoryRole.User
            AssistantRole -> HistoryRole.Assistant
            else -> error("Unknown history message role: $this")
        }

    private companion object {
        const val UserRole = "user"
        const val AssistantRole = "assistant"
    }
}

internal fun Int.toTableName(): String {
    require(this > 0) { "chatId must be positive" }
    return "history_message_tab_$this"
}

internal fun createTableSql(tableName: String): String =
    """
    CREATE TABLE IF NOT EXISTS $tableName (
        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
        role TEXT NOT NULL,
        content TEXT NOT NULL,
        source_label TEXT,
        response_time_ms INTEGER,
        prompt_tokens INTEGER,
        completion_tokens INTEGER,
        total_tokens INTEGER,
        cost REAL,
        retry_count INTEGER
    )
    """.trimIndent()

internal fun insertSql(tableName: String): String =
    """
    INSERT INTO $tableName(
        role,
        content,
        source_label,
        response_time_ms,
        prompt_tokens,
        completion_tokens,
        total_tokens,
        cost,
        retry_count
    )
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    """.trimIndent()

internal fun selectAllSql(tableName: String): String =
    "SELECT * FROM $tableName ORDER BY id ASC"

internal fun deleteAllSql(tableName: String): String =
    "DELETE FROM $tableName"
