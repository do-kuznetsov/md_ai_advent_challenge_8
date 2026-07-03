package com.sibgear.deepseek.chat.data.magnit.external.service

import com.sibgear.deepseek.assistant.memory.domain.model.AssistantInvariant
import com.sibgear.deepseek.assistant.memory.domain.model.InvariantCollectionMessage
import com.sibgear.deepseek.assistant.memory.domain.model.UserProfile
import com.sibgear.deepseek.assistant.memory.domain.service.AssistantInvariantService
import com.sibgear.deepseek.assistant.memory.domain.service.AssistantProfileService
import com.sibgear.deepseek.chat.data.magnit.external.MagnitCopilotBaseUrl
import com.sibgear.deepseek.chat.data.magnit.external.MagnitCopilotProviderLabel

class MagnitCopilotAssistantProfileService(
    apiKey: String,
) : AssistantProfileService,
    AssistantInvariantService {
    private val delegate = MagnitCopilotOpenAiAssistantProfileService(
        apiKey = apiKey,
        baseUrl = MagnitCopilotBaseUrl,
        providerLabel = MagnitCopilotProviderLabel,
    )

    override suspend fun updateProfile(
        currentProfile: UserProfile,
        interviewAnswers: List<String>,
        modelId: String,
    ): UserProfile =
        delegate.updateProfile(currentProfile, interviewAnswers, modelId)

    override suspend fun updateInvariants(
        currentInvariants: List<AssistantInvariant>,
        chatMessages: List<InvariantCollectionMessage>,
        modelId: String,
    ): List<AssistantInvariant> =
        delegate.updateInvariants(currentInvariants, chatMessages, modelId)
}
