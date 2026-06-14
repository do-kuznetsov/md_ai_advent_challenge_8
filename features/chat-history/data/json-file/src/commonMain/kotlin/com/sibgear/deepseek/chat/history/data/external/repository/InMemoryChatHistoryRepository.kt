package com.sibgear.deepseek.chat.history.data.external.repository

import com.sibgear.deepseek.chat.history.domain.repository.ChatHistoryRepository
import com.sibgear.deepseek.chat.history.domain.model.HistoryFact
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessage

class InMemoryChatHistoryRepository : ChatHistoryRepository {
    private var messages: List<HistoryMessage> = emptyList()
    private var facts: List<HistoryFact> = emptyList()

    override suspend fun add(message: HistoryMessage): List<HistoryMessage> {
        messages = messages + message
        return messages
    }

    override suspend fun getMessages(): List<HistoryMessage> = messages

    override suspend fun replace(messages: List<HistoryMessage>): List<HistoryMessage> {
        this.messages = messages
        return this.messages
    }

    override suspend fun getFacts(): List<HistoryFact> = facts

    override suspend fun replaceFacts(facts: List<HistoryFact>): List<HistoryFact> {
        this.facts = facts
        return this.facts
    }

    override suspend fun clear() {
        messages = emptyList()
        facts = emptyList()
    }
}
