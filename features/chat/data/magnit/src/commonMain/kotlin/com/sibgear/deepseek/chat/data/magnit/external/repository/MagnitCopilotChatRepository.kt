package com.sibgear.deepseek.chat.data.magnit.external.repository

import com.sibgear.deepseek.assistant.memory.domain.interactor.AssistantMemoryInteractor
import com.sibgear.deepseek.chat.data.magnit.external.MagnitCopilotBaseUrl
import com.sibgear.deepseek.chat.data.magnit.external.MagnitCopilotProviderLabel
import com.sibgear.deepseek.chat.domain.interactor.ChatContextPlanner
import com.sibgear.deepseek.chat.domain.model.AgentResponse
import com.sibgear.deepseek.chat.domain.model.AiRequestData
import com.sibgear.deepseek.chat.domain.repository.AiChatRepository
import com.sibgear.deepseek.chat.history.domain.interactor.ChatHistoryInteractor

class MagnitCopilotChatRepository(
    apiKey: String,
    historyInteractor: ChatHistoryInteractor,
    memoryInteractor: AssistantMemoryInteractor? = null,
    contextPlanner: ChatContextPlanner = ChatContextPlanner(),
) : AiChatRepository {
    private val delegate = MagnitCopilotOpenAiChatRepository(
        apiKey = apiKey,
        historyInteractor = historyInteractor,
        memoryInteractor = memoryInteractor,
        contextPlanner = contextPlanner,
        baseUrl = MagnitCopilotBaseUrl,
        providerLabel = MagnitCopilotProviderLabel,
        includeUsageCost = false,
    )

    override suspend fun sendMessage(request: AiRequestData): AgentResponse =
        delegate.sendMessage(request)
}
