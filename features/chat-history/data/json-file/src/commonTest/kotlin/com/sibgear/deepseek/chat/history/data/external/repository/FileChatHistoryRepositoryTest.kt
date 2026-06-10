package com.sibgear.deepseek.chat.history.data.external.repository

import com.sibgear.deepseek.chat.history.domain.model.HistoryMessage
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageFooter
import com.sibgear.deepseek.chat.history.domain.model.HistoryRole
import com.sibgear.deepseek.chat.history.data.external.storage.JsonFileChatHistoryStorage
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileChatHistoryRepositoryTest {
    @Test
    fun storesAndRestoresMessagesInInsertionOrder() = runTest {
        val file = tempHistoryFile()
        val repository = FileChatHistoryRepository(file, chatId = 1)
        val first = HistoryMessage(role = HistoryRole.User, content = "hello")
        val second = HistoryMessage(
            role = HistoryRole.Assistant,
            content = "hi",
            sourceLabel = "OpenRouter / test",
            footer = HistoryMessageFooter(
                responseTimeMs = 1_250,
                promptTokens = 10,
                completionTokens = 20,
                totalTokens = 30,
                cost = 0.0,
                retryCount = 1,
            ),
        )

        repository.add(first)
        repository.add(second)

        val restoredRepository = FileChatHistoryRepository(file, chatId = 1)
        assertEquals(listOf(first, second), restoredRepository.getMessages())
    }

    @Test
    fun storesSeveralChatsInSingleFileIndependently() = runTest {
        val file = tempHistoryFile()
        val firstChat = FileChatHistoryRepository(file, chatId = 1)
        val secondChat = FileChatHistoryRepository(file, chatId = 2)
        val firstMessage = HistoryMessage(role = HistoryRole.User, content = "first")
        val secondMessage = HistoryMessage(role = HistoryRole.Assistant, content = "second")

        firstChat.add(firstMessage)
        secondChat.add(secondMessage)

        assertEquals(listOf(firstMessage), FileChatHistoryRepository(file, chatId = 1).getMessages())
        assertEquals(listOf(secondMessage), FileChatHistoryRepository(file, chatId = 2).getMessages())
        assertEquals(listOf(1, 2), JsonFileChatHistoryStorage(file).loadSavedChatIds())
    }

    @Test
    fun replacesMessages() = runTest {
        val file = tempHistoryFile()
        val repository = FileChatHistoryRepository(file, chatId = 1)
        val replacement = listOf(
            HistoryMessage(role = HistoryRole.Assistant, content = "restored"),
        )

        repository.add(HistoryMessage(role = HistoryRole.User, content = "hello"))
        val messages = repository.replace(replacement)

        assertEquals(replacement, messages)
        assertEquals(replacement, FileChatHistoryRepository(file, chatId = 1).getMessages())
    }

    @Test
    fun clearsMessages() = runTest {
        val file = tempHistoryFile()
        val repository = FileChatHistoryRepository(file, chatId = 1)

        repository.add(HistoryMessage(role = HistoryRole.User, content = "hello"))
        repository.clear()

        assertTrue(FileChatHistoryRepository(file, chatId = 1).getMessages().isEmpty())
        assertTrue(JsonFileChatHistoryStorage(file).loadSavedChatIds().isEmpty())
    }

    @Test
    fun corruptFileReturnsEmptyHistoryAndKeepsCorruptCopy() = runTest {
        val file = tempHistoryFile()
        file.parentFile.mkdirs()
        file.writeText("{not-json")

        val repository = FileChatHistoryRepository(file, chatId = 1)

        assertTrue(repository.getMessages().isEmpty())
        assertTrue(File(file.parentFile, "${file.name}.corrupt").exists())
    }

    private fun tempHistoryFile(): File =
        Files.createTempDirectory("ai-clients-history")
            .toFile()
            .resolve("history.json")
}
