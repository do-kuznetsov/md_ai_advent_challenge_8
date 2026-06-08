package com.sibgear.deepseek.data

import com.sibgear.deepseek.history.domain.ChatHistoryRepository
import com.sibgear.deepseek.history.domain.HistoryMessage

class InMemoryChatHistoryRepository : ChatHistoryRepository {
    private var messages: List<HistoryMessage> = emptyList()

    override suspend fun add(message: HistoryMessage): List<HistoryMessage> {
        messages = messages + message
        return messages
    }

    override suspend fun getMessages(): List<HistoryMessage> = messages

    override suspend fun clear() {
        messages = emptyList()
    }
}
