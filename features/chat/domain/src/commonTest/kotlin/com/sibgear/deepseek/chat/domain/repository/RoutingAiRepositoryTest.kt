package com.sibgear.deepseek.chat.domain.repository

import com.sibgear.deepseek.chat.domain.model.AgentResponse
import com.sibgear.deepseek.chat.domain.model.AiModel
import com.sibgear.deepseek.chat.domain.model.AiProvider
import com.sibgear.deepseek.chat.domain.model.AiRequestData
import com.sibgear.deepseek.chat.domain.model.ApiSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RoutingAiRepositoryTest {
    @Test
    fun routesDeepSeekRequestToDeepSeekRepository() = runTest {
        val deepSeekRepository = RecordingChatRepository()
        val openRouterRepository = RecordingChatRepository()
        val routingRepository = RoutingAiRepository(
            chatRepositories = mapOf(
                AiProvider.DeepSeek to deepSeekRepository,
                AiProvider.OpenRouter to openRouterRepository,
            ),
            modelRepositories = emptyMap(),
        )
        val request = request(AiProvider.DeepSeek)

        routingRepository.sendMessage(request)

        assertEquals(request, deepSeekRepository.lastRequest)
        assertEquals(null, openRouterRepository.lastRequest)
    }

    @Test
    fun routesOpenRouterRequestToOpenRouterRepository() = runTest {
        val deepSeekRepository = RecordingChatRepository()
        val openRouterRepository = RecordingChatRepository()
        val routingRepository = RoutingAiRepository(
            chatRepositories = mapOf(
                AiProvider.DeepSeek to deepSeekRepository,
                AiProvider.OpenRouter to openRouterRepository,
            ),
            modelRepositories = emptyMap(),
        )
        val request = request(AiProvider.OpenRouter)

        routingRepository.sendMessage(request)

        assertEquals(null, deepSeekRepository.lastRequest)
        assertEquals(request, openRouterRepository.lastRequest)
    }

    @Test
    fun loadsModelsThroughProviderModelsRepository() = runTest {
        val model = AiModel(id = "openrouter/test", provider = AiProvider.OpenRouter)
        val routingRepository = RoutingAiRepository(
            chatRepositories = emptyMap(),
            modelRepositories = mapOf(AiProvider.OpenRouter to FakeModelsRepository(listOf(model))),
        )

        assertEquals(listOf(model), routingRepository.loadModels(AiProvider.OpenRouter))
    }

    @Test
    fun returnsEmptyModelsWhenProviderModelsRepositoryIsMissing() = runTest {
        val routingRepository = RoutingAiRepository(
            chatRepositories = emptyMap(),
            modelRepositories = emptyMap(),
        )

        assertEquals(emptyList(), routingRepository.loadModels(AiProvider.DeepSeek))
    }

    private fun request(provider: AiProvider): AiRequestData =
        AiRequestData(
            systemPrompt = "",
            prompt = "hello",
            model = AiModel(id = provider.name, provider = provider),
            apiSettings = ApiSettings(),
        )
}

private class RecordingChatRepository : AiChatRepository {
    var lastRequest: AiRequestData? = null

    override suspend fun sendMessage(request: AiRequestData): AgentResponse {
        lastRequest = request
        return AgentResponse(messages = emptyList())
    }
}

private class FakeModelsRepository(
    private val models: List<AiModel>,
) : AiModelsRepository {
    override suspend fun loadModels(): List<AiModel> = models
}
