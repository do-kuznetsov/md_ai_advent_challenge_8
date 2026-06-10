package com.sibgear.deepseek

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.sibgear.deepseek.config.BuildConfig
import com.sibgear.deepseek.chat.data.deepseek.external.repository.DeepSeekChatRepository
import com.sibgear.deepseek.chat.data.deepseek.external.repository.DeepSeekModelsRepository
import com.sibgear.deepseek.chat.data.openrouter.external.repository.OpenRouterChatRepository
import com.sibgear.deepseek.chat.data.openrouter.external.repository.OpenRouterModelsRepository
import com.sibgear.deepseek.chat.domain.model.AiProvider
import com.sibgear.deepseek.chat.domain.interactor.ChatInteractor
import com.sibgear.deepseek.chat.history.data.external.repository.FileChatHistoryRepository
import com.sibgear.deepseek.chat.domain.repository.RoutingAiRepository
import com.sibgear.deepseek.chat.history.domain.interactor.ChatHistoryInteractor
import com.sibgear.deepseek.chat.workspace.ui.external.view.AiChatAppScreen
import com.sibgear.deepseek.chat.workspace.ui.external.presentation.AiChatAppViewModel
import com.sibgear.deepseek.chat.ui.external.presentation.ChatViewModel
import com.sibgear.deepseek.mapper.toChatMessages
import com.sibgear.deepseek.persistence.WorkspaceStorage
import com.sibgear.deepseek.persistence.WorkspaceTabSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    val workspaceStorage = remember { WorkspaceStorage.default() }
    val initialWorkspace = remember(workspaceStorage) { workspaceStorage.load() }
    val historyFileNamesByTab = remember(initialWorkspace) {
        initialWorkspace.tabs
            .associate { it.number to it.historyFileName }
            .toMutableMap()
    }
    val viewModel = remember(scope, workspaceStorage) {
        AiChatAppViewModel(
            createChatViewModel = { tabNumber ->
                val historyFileName = historyFileNamesByTab.getOrPut(tabNumber) {
                    workspaceStorage.defaultHistoryFileName(tabNumber)
                }
                val historyInteractor = ChatHistoryInteractor(
                    repository = FileChatHistoryRepository(
                        file = workspaceStorage.historyFile(historyFileName),
                    ),
                    dispatcher = Dispatchers.Default,
                )
                val initialMessages = runBlocking {
                    historyInteractor.getMessages()
                }.toChatMessages()
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
                    initialMessages = initialMessages,
                )
            },
            initialTabNumbers = initialWorkspace.tabs.map { it.number },
            initialActiveTabNumber = initialWorkspace.activeTabNumber,
            initialNextTabNumber = initialWorkspace.nextTabNumber,
            onWorkspaceChanged = { tabNumbers, activeTabNumber, nextTabNumber ->
                val currentNumbers = tabNumbers.toSet()
                historyFileNamesByTab.keys.retainAll(currentNumbers)
                val tabs = tabNumbers.map { tabNumber ->
                    WorkspaceTabSnapshot(
                        number = tabNumber,
                        historyFileName = historyFileNamesByTab.getOrPut(tabNumber) {
                            workspaceStorage.defaultHistoryFileName(tabNumber)
                        },
                    )
                }
                workspaceStorage.save(
                    tabs = tabs,
                    activeTabNumber = activeTabNumber,
                    nextTabNumber = nextTabNumber,
                )
            },
        )
    }

    AiChatAppScreen(
        state = viewModel.state,
        onEvent = viewModel::onEvent,
    )
}
