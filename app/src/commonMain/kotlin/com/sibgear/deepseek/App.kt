package com.sibgear.deepseek

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.sibgear.deepseek.config.BuildConfig
import com.sibgear.deepseek.data.InMemoryChatHistoryRepository
import com.sibgear.deepseek.data.deepseek.DeepSeekChatRepository
import com.sibgear.deepseek.data.deepseek.DeepSeekModelsRepository
import com.sibgear.deepseek.data.openrouter.OpenRouterChatRepository
import com.sibgear.deepseek.data.openrouter.OpenRouterModelsRepository
import com.sibgear.deepseek.domain.AiProvider
import com.sibgear.deepseek.domain.ChatInteractor
import com.sibgear.deepseek.domain.RoutingAiRepository
import com.sibgear.deepseek.history.domain.ChatHistoryInteractor
import com.sibgear.deepseek.ui.AiChatAppScreen
import com.sibgear.deepseek.ui.AiChatAppViewModel
import com.sibgear.deepseek.ui.ChatViewModel
import kotlinx.coroutines.Dispatchers

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    val viewModel = remember(scope) {
        AiChatAppViewModel(
            createChatViewModel = {
                val historyInteractor = ChatHistoryInteractor(
                    repository = InMemoryChatHistoryRepository(),
                    dispatcher = Dispatchers.Default,
                )
                val repository = RoutingAiRepository(
                    chatRepositories = mapOf(
                        AiProvider.DeepSeek to DeepSeekChatRepository(
                            apiKey = BuildConfig.DEEPSEEK_API_KEY,
                            historyInteractor = historyInteractor,
                        ),
                        AiProvider.OpenRouter to OpenRouterChatRepository(
                            apiKey = BuildConfig.OPENROUTER_AI_KEY,
                            historyInteractor = historyInteractor,
                        ),
                    ),
                    modelRepositories = mapOf(
                        AiProvider.DeepSeek to DeepSeekModelsRepository(),
                        AiProvider.OpenRouter to OpenRouterModelsRepository(
                            apiKey = BuildConfig.OPENROUTER_AI_KEY,
                        ),
                    ),
                )
                val interactor = ChatInteractor(
                    repository = repository,
                    dispatcher = Dispatchers.IO,
                )

                ChatViewModel(
                    interactor = interactor,
                    coroutineScope = scope,
                )
            },
        )
    }

    AiChatAppScreen(
        state = viewModel.state,
        onEvent = viewModel::onEvent,
    )
}
