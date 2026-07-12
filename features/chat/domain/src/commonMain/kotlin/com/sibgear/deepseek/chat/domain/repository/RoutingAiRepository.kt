package com.sibgear.deepseek.chat.domain.repository

import com.sibgear.deepseek.chat.domain.model.AgentResponse
import com.sibgear.deepseek.chat.domain.model.AiModel
import com.sibgear.deepseek.chat.domain.model.AiProvider
import com.sibgear.deepseek.chat.domain.model.AiRequestData
import com.sibgear.deepseek.chat.domain.model.ChatMessage
import com.sibgear.deepseek.chat.domain.model.ChatRole
import com.sibgear.deepseek.chat.domain.model.StreamingChatDelta

class RoutingAiRepository(
    private val chatRepositories: Map<AiProvider, AiChatRepository>,
    private val modelRepositories: Map<AiProvider, AiModelsRepository>,
) {
    suspend fun sendMessage(request: AiRequestData): AgentResponse {
        val repository = chatRepositories[request.model.provider]
            ?: return AgentResponse(
                messages = listOf(
                    ChatMessage(
                        role = ChatRole.Assistant,
                        content = "Repository for ${request.model.provider} is not configured.",
                    ),
                ),
            )

        return repository.sendMessage(request)
    }

    suspend fun sendStreamingMessage(
        request: AiRequestData,
        onDelta: suspend (StreamingChatDelta) -> Unit,
    ): AgentResponse {
        val repository = chatRepositories[request.model.provider]
        if (repository !is StreamingAiChatRepository) {
            return sendMessage(request)
        }

        return repository.sendStreamingMessage(request, onDelta)
    }

    suspend fun loadModels(provider: AiProvider): List<AiModel> =
        modelRepositories[provider]?.loadModels().orEmpty()
}
