package com.sibgear.deepseek.chat.domain.repository

import com.sibgear.deepseek.chat.domain.model.AgentResponse
import com.sibgear.deepseek.chat.domain.model.AiRequestData
import com.sibgear.deepseek.chat.domain.model.StreamingChatDelta

interface StreamingAiChatRepository : AiChatRepository {
    suspend fun sendStreamingMessage(
        request: AiRequestData,
        onDelta: suspend (StreamingChatDelta) -> Unit,
    ): AgentResponse
}
