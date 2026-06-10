package com.sibgear.deepseek.chat.data.deepseek.external.repository

import com.sibgear.deepseek.chat.domain.model.AiModel
import com.sibgear.deepseek.chat.domain.model.AiProvider
import com.sibgear.deepseek.chat.domain.repository.AiModelsRepository

class DeepSeekModelsRepository : AiModelsRepository {
    override suspend fun loadModels(): List<AiModel> =
        listOf(
            AiModel(id = "deepseek-v4-flash", provider = AiProvider.DeepSeek),
            AiModel(id = "deepseek-v4-pro", provider = AiProvider.DeepSeek),
            AiModel(id = "deepseek-chat", provider = AiProvider.DeepSeek),
            AiModel(id = "deepseek-reasoner", provider = AiProvider.DeepSeek),
        )
}
