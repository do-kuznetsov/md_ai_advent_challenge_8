package com.sibgear.deepseek.chat.history.domain.repository

import com.sibgear.deepseek.chat.history.domain.model.HistoryMessage
import com.sibgear.deepseek.chat.history.domain.model.HistoryFact
import com.sibgear.deepseek.chat.history.domain.model.HistoryBranch

interface ChatHistoryRepository {
    suspend fun add(message: HistoryMessage): List<HistoryMessage>
    suspend fun getMessages(): List<HistoryMessage>
    suspend fun replace(messages: List<HistoryMessage>): List<HistoryMessage>
    suspend fun getFacts(): List<HistoryFact>
    suspend fun replaceFacts(facts: List<HistoryFact>): List<HistoryFact>
    suspend fun getBranches(): List<HistoryBranch>
    suspend fun replaceBranches(branches: List<HistoryBranch>): List<HistoryBranch>
    suspend fun clear()
}
