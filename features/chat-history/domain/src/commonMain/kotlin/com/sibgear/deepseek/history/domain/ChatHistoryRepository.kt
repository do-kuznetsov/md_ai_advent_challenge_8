package com.sibgear.deepseek.history.domain

interface ChatHistoryRepository {
    suspend fun add(message: HistoryMessage): List<HistoryMessage>
    suspend fun getMessages(): List<HistoryMessage>
    suspend fun clear()
}
