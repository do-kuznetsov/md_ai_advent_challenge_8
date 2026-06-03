package com.sibgear.deepseek.domain

interface DeepSeekRepository {
    suspend fun sendMessage(request: DeepSeekRequestData): AgentResponse
}
