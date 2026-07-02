package com.sibgear.deepseek.chat.data.magnit.external.repository

import com.sibgear.deepseek.chat.data.magnit.external.MagnitCopilotContextLength
import com.sibgear.deepseek.chat.data.magnit.external.MagnitCopilotModelId
import com.sibgear.deepseek.chat.data.magnit.external.MagnitCopilotProviderLabel
import com.sibgear.deepseek.chat.domain.model.AiModel
import com.sibgear.deepseek.chat.domain.model.AiProvider
import com.sibgear.deepseek.chat.domain.repository.AiModelsRepository

class MagnitCopilotModelsRepository : AiModelsRepository {
    override suspend fun loadModels(): List<AiModel> =
        listOf(
            AiModel(
                id = MagnitCopilotModelId,
                displayName = MagnitCopilotProviderLabel,
                provider = AiProvider.MagnitCopilot,
                description = "LiteLLM Copilot prod",
                contextLength = MagnitCopilotContextLength,
                supportedParameters = listOf("temperature", "max_tokens", "stop"),
            ),
        )
}
