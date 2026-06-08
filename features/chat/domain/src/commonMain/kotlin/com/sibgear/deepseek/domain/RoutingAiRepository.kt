package com.sibgear.deepseek.domain

class RoutingAiRepository(
    private val chatRepositories: Map<AiProvider, AiChatRepository>,
    private val openRouterModelsRepository: AiModelsRepository?,
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

    suspend fun loadOpenRouterModels(): List<AiModel> =
        openRouterModelsRepository?.loadModels().orEmpty()
}
