package com.sibgear.deepseek.data.deepseek

import com.sibgear.deepseek.domain.AiModel
import com.sibgear.deepseek.domain.AiModelsRepository
import com.sibgear.deepseek.domain.AiProvider

class DeepSeekModelsRepository : AiModelsRepository {
    override suspend fun loadModels(): List<AiModel> =
        listOf(
            AiModel(id = "deepseek-v4-flash", provider = AiProvider.DeepSeek),
            AiModel(id = "deepseek-v4-pro", provider = AiProvider.DeepSeek),
            AiModel(id = "deepseek-chat", provider = AiProvider.DeepSeek),
            AiModel(id = "deepseek-reasoner", provider = AiProvider.DeepSeek),
        )
}
