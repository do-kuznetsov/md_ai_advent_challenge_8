package com.sibgear.deepseek.chat.history.data.sqldelight.external.storage

import com.sibgear.deepseek.chat.history.data.sqldelight.external.repository.SqldelightChatHistoryRepository
import com.sibgear.deepseek.chat.history.data.sqldelight.external.repository.toTableName
import java.io.File
import java.sql.DriverManager

class SqldelightChatHistoryStorage(
    private val databaseFile: File,
) {
    fun loadSavedChatIds(): List<Int> {
        if (!databaseFile.exists()) {
            return emptyList()
        }

        return DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}").use { connection ->
            connection.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name LIKE 'history_message_tab_%'",
            ).use { statement ->
                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            resultSet.getString("name")
                                .removePrefix("history_message_tab_")
                                .toIntOrNull()
                                ?.let(::add)
                        }
                    }.sorted()
                }
            }
        }
    }

    fun createRepository(chatId: Int): SqldelightChatHistoryRepository =
        SqldelightChatHistoryRepository(
            databaseFile = databaseFile,
            chatId = chatId,
        )

    fun deleteChat(chatId: Int) {
        databaseFile.parentFile?.mkdirs()
        DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("DROP TABLE IF EXISTS ${chatId.toTableName()}")
            }
        }
    }
}
