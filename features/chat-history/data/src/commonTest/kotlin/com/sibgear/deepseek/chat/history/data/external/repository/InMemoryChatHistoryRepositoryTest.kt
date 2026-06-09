package com.sibgear.deepseek.chat.history.data.external.repository

import com.sibgear.deepseek.chat.history.domain.model.HistoryMessage
import com.sibgear.deepseek.chat.history.domain.model.HistoryRole
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InMemoryChatHistoryRepositoryTest {
    @Test
    fun storesMessagesInInsertionOrder() = runTest {
        val repository = InMemoryChatHistoryRepository()
        val first = HistoryMessage(role = HistoryRole.User, content = "hello")
        val second = HistoryMessage(role = HistoryRole.Assistant, content = "hi")

        repository.add(first)
        val messages = repository.add(second)

        assertEquals(listOf(first, second), messages)
        assertEquals(listOf(first, second), repository.getMessages())
    }

    @Test
    fun clearsMessages() = runTest {
        val repository = InMemoryChatHistoryRepository()

        repository.add(HistoryMessage(role = HistoryRole.User, content = "hello"))
        repository.clear()

        assertTrue(repository.getMessages().isEmpty())
    }
}
