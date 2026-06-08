package com.sibgear.deepseek.domain

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class ChatInteractor(
    private val repository: RoutingAiRepository,
    private val dispatcher: CoroutineDispatcher,
) {
    suspend fun sendMessage(request: AiRequestData): AgentResponse =
        withContext(dispatcher) {
            repository.sendMessage(request)
        }

    suspend fun loadOpenRouterModels(): List<AiModel> =
        withContext(dispatcher) {
            repository.loadOpenRouterModels()
        }
}
