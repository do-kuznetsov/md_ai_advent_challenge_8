package com.sibgear.deepseek.assistant.memory.domain.service

import com.sibgear.deepseek.assistant.memory.domain.model.AssistantInvariant
import com.sibgear.deepseek.assistant.memory.domain.model.InvariantCollectionMessage

interface AssistantInvariantService {
    suspend fun updateInvariants(
        currentInvariants: List<AssistantInvariant>,
        chatMessages: List<InvariantCollectionMessage>,
        modelId: String,
    ): List<AssistantInvariant>
}
