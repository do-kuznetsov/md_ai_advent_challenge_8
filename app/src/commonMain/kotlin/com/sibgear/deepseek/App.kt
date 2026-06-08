package com.sibgear.deepseek

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.sibgear.deepseek.config.BuildConfig
import com.sibgear.deepseek.data.AiProviderCredentials
import com.sibgear.deepseek.data.KtorAiRepository
import com.sibgear.deepseek.domain.ChatInteractor
import com.sibgear.deepseek.ui.AiChatAppScreen
import com.sibgear.deepseek.ui.AiChatAppViewModel
import com.sibgear.deepseek.ui.ChatViewModel
import kotlinx.coroutines.Dispatchers

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    val viewModel = remember(scope) {
        val credentials = AiProviderCredentials(
            deepSeekApiKey = BuildConfig.DEEPSEEK_API_KEY,
            openRouterAiKey = BuildConfig.OPENROUTER_AI_KEY,
        )

        AiChatAppViewModel(
            createChatViewModel = {
                val repository = KtorAiRepository(credentials = credentials)
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
