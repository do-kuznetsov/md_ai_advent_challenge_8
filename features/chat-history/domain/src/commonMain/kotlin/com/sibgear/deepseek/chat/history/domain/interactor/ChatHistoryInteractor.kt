package com.sibgear.deepseek.chat.history.domain.interactor

import com.sibgear.deepseek.chat.history.domain.model.HistoryMessage
import com.sibgear.deepseek.chat.history.domain.repository.ChatHistoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class ChatHistoryInteractor(
    private val repository: ChatHistoryRepository,
    private val dispatcher: CoroutineDispatcher,
) {
    suspend fun add(message: HistoryMessage): List<HistoryMessage> =
        withContext(dispatcher) {
            repository.add(message)
        }

    suspend fun getMessages(): List<HistoryMessage> =
        withContext(dispatcher) {
            repository.getMessages()
        }

    suspend fun clear() {
        withContext(dispatcher) {
            repository.clear()
        }
    }
}
