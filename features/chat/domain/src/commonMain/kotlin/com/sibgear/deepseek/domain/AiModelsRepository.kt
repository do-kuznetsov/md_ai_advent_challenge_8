package com.sibgear.deepseek.domain

interface AiModelsRepository {
    suspend fun loadModels(): List<AiModel>
}
