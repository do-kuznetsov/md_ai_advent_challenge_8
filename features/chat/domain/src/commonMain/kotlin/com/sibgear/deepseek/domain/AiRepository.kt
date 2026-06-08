package com.sibgear.deepseek.domain

interface AiRepository {
    suspend fun sendMessage(request: AiRequestData): AgentResponse
    suspend fun loadOpenRouterModels(): List<AiModel>
}
