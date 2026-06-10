package com.sibgear.deepseek.chat.domain.repository

import com.sibgear.deepseek.chat.domain.model.AgentResponse
import com.sibgear.deepseek.chat.domain.model.AiRequestData

interface AiChatRepository {
    suspend fun sendMessage(request: AiRequestData): AgentResponse
}
