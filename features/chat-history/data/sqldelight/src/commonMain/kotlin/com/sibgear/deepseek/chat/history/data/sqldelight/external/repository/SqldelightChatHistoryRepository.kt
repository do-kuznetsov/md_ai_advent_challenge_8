package com.sibgear.deepseek.chat.history.data.sqldelight.external.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.sibgear.deepseek.chat.history.data.sqldelight.internal.database.ChatHistoryDatabase
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessage
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageFooter
import com.sibgear.deepseek.chat.history.domain.model.HistoryRole
import com.sibgear.deepseek.chat.history.domain.repository.ChatHistoryRepository
import java.io.File

class SqldelightChatHistoryRepository(
    private val databaseFile: File,
) : ChatHistoryRepository {
    private val database: ChatHistoryDatabase
    private val queries: com.sibgear.deepseek.chat.history.data.sqldelight.internal.database.ChatHistoryQueries

    init {
        databaseFile.parentFile?.mkdirs()
        val shouldCreateSchema = !databaseFile.exists() || databaseFile.length() == 0L
        val driver = JdbcSqliteDriver("jdbc:sqlite:${databaseFile.absolutePath}")
        database = ChatHistoryDatabase(driver)
        queries = database.chatHistoryQueries
        if (shouldCreateSchema) {
            ChatHistoryDatabase.Schema.create(driver)
        }
    }

    override suspend fun add(message: HistoryMessage): List<HistoryMessage> {
        queries.insertMessage(
            role = message.role.databaseValue,
            content = message.content,
            source_label = message.sourceLabel,
            response_time_ms = message.footer?.responseTimeMs,
            prompt_tokens = message.footer?.promptTokens?.toLong(),
            completion_tokens = message.footer?.completionTokens?.toLong(),
            total_tokens = message.footer?.totalTokens?.toLong(),
            cost = message.footer?.cost,
            retry_count = message.footer?.retryCount?.toLong(),
        )
        return getMessages()
    }

    override suspend fun getMessages(): List<HistoryMessage> =
        queries.selectAll { _, role, content, sourceLabel, responseTimeMs, promptTokens, completionTokens, totalTokens, cost, retryCount ->
            HistoryMessage(
                role = role.toHistoryRole(),
                content = content,
                sourceLabel = sourceLabel,
                footer = responseTimeMs?.let {
                    HistoryMessageFooter(
                        responseTimeMs = it,
                        promptTokens = promptTokens?.toInt(),
                        completionTokens = completionTokens?.toInt(),
                        totalTokens = totalTokens?.toInt(),
                        cost = cost,
                        retryCount = retryCount?.toInt() ?: 0,
                    )
                },
            )
        }.executeAsList()

    override suspend fun replace(messages: List<HistoryMessage>): List<HistoryMessage> {
        queries.transaction {
            queries.deleteAll()
            messages.forEach { message ->
                queries.insertMessage(
                    role = message.role.databaseValue,
                    content = message.content,
                    source_label = message.sourceLabel,
                    response_time_ms = message.footer?.responseTimeMs,
                    prompt_tokens = message.footer?.promptTokens?.toLong(),
                    completion_tokens = message.footer?.completionTokens?.toLong(),
                    total_tokens = message.footer?.totalTokens?.toLong(),
                    cost = message.footer?.cost,
                    retry_count = message.footer?.retryCount?.toLong(),
                )
            }
        }
        return getMessages()
    }

    override suspend fun clear() {
        queries.deleteAll()
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
