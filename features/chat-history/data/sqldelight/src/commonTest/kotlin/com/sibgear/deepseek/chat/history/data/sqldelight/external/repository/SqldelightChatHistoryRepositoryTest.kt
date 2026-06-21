package com.sibgear.deepseek.chat.history.data.sqldelight.external.repository

import com.sibgear.deepseek.chat.history.domain.model.HistoryMessage
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageAttachment
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageFooter
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageKind
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageMemoryMetadata
import com.sibgear.deepseek.chat.history.domain.model.HistoryMemoryChange
import com.sibgear.deepseek.chat.history.domain.model.HistoryMemoryChangeAction
import com.sibgear.deepseek.chat.history.domain.model.HistoryMemoryItem
import com.sibgear.deepseek.chat.history.domain.model.HistoryMemoryLayer
import com.sibgear.deepseek.chat.history.domain.model.HistoryRole
import com.sibgear.deepseek.chat.history.domain.model.HistoryBranch
import com.sibgear.deepseek.chat.history.data.sqldelight.external.storage.SqldelightChatHistoryStorage
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SqldelightChatHistoryRepositoryTest {
    @Test
    fun storesMessagesInInsertionOrder() = runTest {
        val repository = SqldelightChatHistoryRepository(tempDatabaseFile(), chatId = 1)
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
        val firstRepository = SqldelightChatHistoryRepository(file, chatId = 1)
        val message = HistoryMessage(
            role = HistoryRole.Assistant,
            content = "stored",
            branchId = 2,
            kind = HistoryMessageKind.CompressionSummary,
            apiContent = "stored api content",
            attachment = HistoryMessageAttachment(
                fileName = "data.json",
                sizeBytes = 512L,
            ),
            memory = HistoryMessageMemoryMetadata(
                storedLayers = listOf(HistoryMemoryLayer.WorkingMemory),
                usedLayers = listOf(HistoryMemoryLayer.ShortTerm, HistoryMemoryLayer.WorkingMemory),
                changes = listOf(
                    HistoryMemoryChange(
                        action = HistoryMemoryChangeAction.Add,
                        layer = HistoryMemoryLayer.WorkingMemory,
                        fact = "Project uses Kotlin",
                    ),
                ),
                injectedItems = listOf(
                    HistoryMemoryItem(
                        id = "memory-1",
                        layer = HistoryMemoryLayer.WorkingMemory,
                        fact = "Project uses Kotlin",
                        importance = 0.8,
                    ),
                ),
            ),
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

        assertEquals(listOf(message), SqldelightChatHistoryRepository(file, chatId = 1).getMessages())
    }

    @Test
    fun storesSeveralChatsInSingleDatabaseIndependently() = runTest {
        val file = tempDatabaseFile()
        val storage = SqldelightChatHistoryStorage(file)
        val firstChat = storage.createRepository(chatId = 1)
        val secondChat = storage.createRepository(chatId = 2)
        val firstMessage = HistoryMessage(role = HistoryRole.User, content = "first")
        val secondMessage = HistoryMessage(role = HistoryRole.Assistant, content = "second")

        firstChat.add(firstMessage)
        secondChat.add(secondMessage)

        assertEquals(listOf(firstMessage), storage.createRepository(chatId = 1).getMessages())
        assertEquals(listOf(secondMessage), storage.createRepository(chatId = 2).getMessages())
        assertEquals(listOf(1, 2), storage.loadSavedChatIds())
    }

    @Test
    fun storesAndRestoresTaskStateEventKind() = runTest {
        val file = tempDatabaseFile()
        val repository = SqldelightChatHistoryRepository(file, chatId = 1)
        val event = HistoryMessage(
            role = HistoryRole.Assistant,
            content = "Stage completed: planning",
            kind = HistoryMessageKind.TaskStateEvent,
        )

        repository.add(event)

        assertEquals(listOf(event), SqldelightChatHistoryRepository(file, chatId = 1).getMessages())
    }

    @Test
    fun replacesMessages() = runTest {
        val repository = SqldelightChatHistoryRepository(tempDatabaseFile(), chatId = 1)
        val replacement = listOf(
            HistoryMessage(role = HistoryRole.Assistant, content = "new"),
        )

        repository.add(HistoryMessage(role = HistoryRole.User, content = "old"))
        val messages = repository.replace(replacement)

        assertEquals(replacement, messages)
        assertEquals(replacement, repository.getMessages())
    }

    @Test
    fun storesAndRestoresBranchesFromSameDatabaseFile() = runTest {
        val file = tempDatabaseFile()
        val repository = SqldelightChatHistoryRepository(file, chatId = 1)
        val branches = listOf(
            HistoryBranch(id = 1, title = "техника", summary = "про технику"),
            HistoryBranch(id = 2, parentId = 1, title = "автомобили", summary = "про автомобили"),
        )

        repository.replaceBranches(branches)

        assertEquals(branches, SqldelightChatHistoryRepository(file, chatId = 1).getBranches())
    }

    @Test
    fun clearsMessages() = runTest {
        val repository = SqldelightChatHistoryRepository(tempDatabaseFile(), chatId = 1)

        repository.add(HistoryMessage(role = HistoryRole.User, content = "hello"))
        repository.clear()

        assertTrue(repository.getMessages().isEmpty())
    }

    @Test
    fun deletesChatTable() = runTest {
        val file = tempDatabaseFile()
        val storage = SqldelightChatHistoryStorage(file)

        storage.createRepository(chatId = 1).add(HistoryMessage(role = HistoryRole.User, content = "hello"))
        storage.deleteChat(chatId = 1)

        assertTrue(storage.loadSavedChatIds().isEmpty())
    }

    private fun tempDatabaseFile(): File =
        Files.createTempDirectory("ai-clients-sqldelight-history")
            .toFile()
            .resolve("history.db")
}
