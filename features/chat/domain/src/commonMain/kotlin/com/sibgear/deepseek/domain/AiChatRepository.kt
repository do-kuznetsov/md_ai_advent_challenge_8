package com.sibgear.deepseek.domain

interface AiChatRepository {
    suspend fun sendMessage(request: AiRequestData): AgentResponse
}
