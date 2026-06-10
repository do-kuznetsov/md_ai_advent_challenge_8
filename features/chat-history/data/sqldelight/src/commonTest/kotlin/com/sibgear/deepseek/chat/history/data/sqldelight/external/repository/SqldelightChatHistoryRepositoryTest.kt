package com.sibgear.deepseek.chat.history.data.sqldelight.external.repository

import com.sibgear.deepseek.chat.history.domain.model.HistoryMessage
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageFooter
import com.sibgear.deepseek.chat.history.domain.model.HistoryRole
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SqldelightChatHistoryRepositoryTest {
    @Test
    fun storesMessagesInInsertionOrder() = runTest {
        val repository = SqldelightChatHistoryRepository(tempDatabaseFile())
        val first = HistoryMessage(role = HistoryRole.User, content = "hello")
        val second = HistoryMessage(role = HistoryRole.Assistant, content = "hi")

        repository.add(first)
        val messages = repository.add(second)

        assertEquals(listOf(first, second), messages)
        assertEquals(listOf(first, second), repository.getMessages())
    }

    @Test
    fun restoresMessagesFromSameDatabaseFile() = runTest {
        val file = tempDatabaseFile()
        val firstRepository = SqldelightChatHistoryRepository(file)
        val message = HistoryMessage(
            role = HistoryRole.Assistant,
            content = "stored",
            sourceLabel = "DeepSeek / test",
            footer = HistoryMessageFooter(
                responseTimeMs = 1_500,
                promptTokens = 11,
                completionTokens = 22,
                totalTokens = 33,
                cost = 0.001,
                retryCount = 1,
            ),
        )

        firstRepository.add(message)

        assertEquals(listOf(message), SqldelightChatHistoryRepository(file).getMessages())
    }

    @Test
    fun replacesMessages() = runTest {
        val repository = SqldelightChatHistoryRepository(tempDatabaseFile())
        val replacement = listOf(
            HistoryMessage(role = HistoryRole.Assistant, content = "new"),
        )

        repository.add(HistoryMessage(role = HistoryRole.User, content = "old"))
        val messages = repository.replace(replacement)

        assertEquals(replacement, messages)
        assertEquals(replacement, repository.getMessages())
    }

    @Test
    fun clearsMessages() = runTest {
        val repository = SqldelightChatHistoryRepository(tempDatabaseFile())

        repository.add(HistoryMessage(role = HistoryRole.User, content = "hello"))
        repository.clear()

        assertTrue(repository.getMessages().isEmpty())
    }

    private fun tempDatabaseFile(): File =
        Files.createTempDirectory("ai-clients-sqldelight-history")
            .toFile()
            .resolve("history.db")
}
