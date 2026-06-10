package com.sibgear.deepseek.chat.domain.repository

import com.sibgear.deepseek.chat.domain.model.AiModel

interface AiModelsRepository {
    suspend fun loadModels(): List<AiModel>
}
