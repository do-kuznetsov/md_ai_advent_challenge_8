package com.sibgear.deepseek.chat.history.domain.repository

import com.sibgear.deepseek.chat.history.domain.model.HistoryMessage

interface ChatHistoryRepository {
    suspend fun add(message: HistoryMessage): List<HistoryMessage>
    suspend fun getMessages(): List<HistoryMessage>
    suspend fun replace(messages: List<HistoryMessage>): List<HistoryMessage>
    suspend fun clear()
}
