package com.sibgear.deepseek.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sibgear.deepseek.config.BuildConfig
import com.sibgear.deepseek.data.KtorAiRepository
import kotlinx.coroutines.CoroutineScope

class AiChatAppViewModel(
    private val coroutineScope: CoroutineScope,
) {
    private var nextTabNumber = 1

    var state by mutableStateOf(
        createInitialState(),
    )
        private set

    fun onEvent(event: AiChatAppEvent) {
        when (event) {
            is AiChatAppEvent.ActiveChatEvent -> handleChatEvent(event.event)
            AiChatAppEvent.TabAdded -> addTab()
            is AiChatAppEvent.TabClosed -> closeTab(event.number)
            is AiChatAppEvent.TabSelected -> {
                if (state.tabs.any { it.number == event.number }) {
                    state = state.copy(activeTabNumber = event.number)
                }
            }
        }
    }

    private fun createInitialState(): AiChatAppViewState {
        val firstTab = createTab()
        return AiChatAppViewState(
            tabs = listOf(firstTab),
            activeTabNumber = firstTab.number,
        )
    }

    private fun createTab(): ChatTab {
        val number = nextTabNumber
        nextTabNumber += 1
        val viewModel = ChatViewModel(
            repository = KtorAiRepository(),
            coroutineScope = coroutineScope,
        )
        viewModel.loadOpenRouterModels(BuildConfig.OPENROUTER_AI_KEY)

        return ChatTab(
            number = number,
            viewModel = viewModel,
        )
    }

    private fun addTab() {
        val tab = createTab()
        state = state.copy(
            tabs = state.tabs + tab,
            activeTabNumber = tab.number,
        )
    }

    private fun closeTab(number: Int) {
        val currentTabs = state.tabs
        val closingIndex = currentTabs.indexOfFirst { it.number == number }
        if (closingIndex == -1) {
            return
        }

        val remainingTabs = currentTabs.filterNot { it.number == number }
        if (remainingTabs.isEmpty()) {
            val replacementTab = createTab()
            state = state.copy(
                tabs = listOf(replacementTab),
                activeTabNumber = replacementTab.number,
            )
            return
        }

        val activeTabNumber = if (state.activeTabNumber == number) {
            val rightNeighbor = currentTabs.drop(closingIndex + 1).firstOrNull()
            val leftNeighbor = currentTabs.take(closingIndex).lastOrNull()
            rightNeighbor?.number ?: leftNeighbor?.number ?: remainingTabs.first().number
        } else {
            state.activeTabNumber
        }

        state = state.copy(
            tabs = remainingTabs,
            activeTabNumber = activeTabNumber,
        )
    }

    private fun handleChatEvent(event: ChatEvent) {
        val activeViewModel = state.activeTab?.viewModel ?: return
        when (event) {
            ChatEvent.SendClicked -> activeViewModel.sendPrompt(
                deepSeekApiKey = BuildConfig.DEEPSEEK_API_KEY,
                openRouterApiKey = BuildConfig.OPENROUTER_AI_KEY,
            )
            else -> activeViewModel.onEvent(event)
        }
    }
}
