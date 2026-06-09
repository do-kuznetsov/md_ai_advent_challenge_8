package com.sibgear.deepseek

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.sibgear.deepseek.config.BuildConfig
import com.sibgear.deepseek.chat.history.data.external.repository.InMemoryChatHistoryRepository
import com.sibgear.deepseek.chat.data.deepseek.external.repository.DeepSeekChatRepository
import com.sibgear.deepseek.chat.data.deepseek.external.repository.DeepSeekModelsRepository
import com.sibgear.deepseek.chat.data.openrouter.external.repository.OpenRouterChatRepository
import com.sibgear.deepseek.chat.data.openrouter.external.repository.OpenRouterModelsRepository
import com.sibgear.deepseek.chat.domain.model.AiProvider
import com.sibgear.deepseek.chat.domain.interactor.ChatInteractor
import com.sibgear.deepseek.chat.domain.repository.RoutingAiRepository
import com.sibgear.deepseek.chat.history.domain.interactor.ChatHistoryInteractor
import com.sibgear.deepseek.chat.workspace.ui.external.view.AiChatAppScreen
import com.sibgear.deepseek.chat.workspace.ui.external.presentation.AiChatAppViewModel
import com.sibgear.deepseek.chat.ui.external.presentation.ChatViewModel
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
