package com.sibgear.deepseek.chat.domain.interactor

import com.sibgear.deepseek.chat.domain.model.AgentResponse
import com.sibgear.deepseek.chat.domain.model.AiModel
import com.sibgear.deepseek.chat.domain.model.AiProvider
import com.sibgear.deepseek.chat.domain.model.AiRequestData
import com.sibgear.deepseek.chat.domain.model.StreamingChatDelta
import com.sibgear.deepseek.chat.domain.repository.RoutingAiRepository
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

    suspend fun sendStreamingMessage(
        request: AiRequestData,
        onDelta: suspend (StreamingChatDelta) -> Unit,
    ): AgentResponse =
        withContext(dispatcher) {
            repository.sendStreamingMessage(request, onDelta)
        }

    suspend fun loadModels(provider: AiProvider): List<AiModel> =
        withContext(dispatcher) {
            repository.loadModels(provider)
        }
}
