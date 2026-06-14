package com.sibgear.deepseek.chat.history.data.external.repository

import com.sibgear.deepseek.chat.history.domain.model.HistoryBranch
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

    @Test
    fun replacesMessages() = runTest {
        val repository = InMemoryChatHistoryRepository()
        val replacement = listOf(
            HistoryMessage(role = HistoryRole.Assistant, content = "restored"),
        )

        repository.add(HistoryMessage(role = HistoryRole.User, content = "hello"))
        val messages = repository.replace(replacement)

        assertEquals(replacement, messages)
        assertEquals(replacement, repository.getMessages())
    }

    @Test
    fun storesAndClearsBranches() = runTest {
        val repository = InMemoryChatHistoryRepository()
        val branches = listOf(
            HistoryBranch(id = 1, title = "main", summary = "main"),
        )

        repository.replaceBranches(branches)
        assertEquals(branches, repository.getBranches())

        repository.clear()
        assertTrue(repository.getBranches().isEmpty())
    }
}
