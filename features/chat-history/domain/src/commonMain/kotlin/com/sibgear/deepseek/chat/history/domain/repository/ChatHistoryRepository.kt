package com.sibgear.deepseek.chat.history.domain.repository

import com.sibgear.deepseek.chat.history.domain.model.HistoryMessage
import com.sibgear.deepseek.chat.history.domain.model.HistoryFact

interface ChatHistoryRepository {
    suspend fun add(message: HistoryMessage): List<HistoryMessage>
    suspend fun getMessages(): List<HistoryMessage>
    suspend fun replace(messages: List<HistoryMessage>): List<HistoryMessage>
    suspend fun getFacts(): List<HistoryFact>
    suspend fun replaceFacts(facts: List<HistoryFact>): List<HistoryFact>
    suspend fun clear()
}
