package com.sibgear.deepseek.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sibgear.deepseek.data.KtorDeepSeekRepository
import kotlinx.coroutines.CoroutineScope

class DeepSeekAppViewModel(
    private val coroutineScope: CoroutineScope,
) {
    private var nextTabNumber = 1

    var state by mutableStateOf(
        createInitialState(),
    )
        private set

    fun onEvent(event: DeepSeekAppEvent) {
        when (event) {
            is DeepSeekAppEvent.ActiveChatEvent -> handleChatEvent(event.event)
            is DeepSeekAppEvent.ApiKeyInputChanged -> {
                state = state.copy(apiKeyInput = event.apiKey)
            }
            DeepSeekAppEvent.ApiKeyConfirmed -> confirmApiKey()
            DeepSeekAppEvent.TabAdded -> addTab()
            is DeepSeekAppEvent.TabClosed -> closeTab(event.number)
            is DeepSeekAppEvent.TabSelected -> {
                if (state.tabs.any { it.number == event.number }) {
                    state = state.copy(activeTabNumber = event.number)
                }
            }
        }
    }

    private fun createInitialState(): DeepSeekAppViewState {
        val firstTab = createTab()
        return DeepSeekAppViewState(
            tabs = listOf(firstTab),
            activeTabNumber = firstTab.number,
        )
    }

    private fun createTab(): ChatTab {
        val number = nextTabNumber
        nextTabNumber += 1
        return ChatTab(
            number = number,
            viewModel = DeepSeekViewModel(
                repository = KtorDeepSeekRepository(),
                coroutineScope = coroutineScope,
            ),
        )
    }

    private fun confirmApiKey() {
        val trimmedApiKey = state.apiKeyInput.trim()
        if (trimmedApiKey.isEmpty()) {
            return
        }

        state = state.copy(
            apiKey = trimmedApiKey,
            isApiKeyDialogVisible = false,
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

    private fun handleChatEvent(event: DeepSeekViewEvent) {
        val activeViewModel = state.activeTab?.viewModel ?: return
        when (event) {
            DeepSeekViewEvent.SendClicked -> {
                if (state.apiKey.isNotBlank()) {
                    activeViewModel.sendPrompt(state.apiKey)
                }
            }
            else -> activeViewModel.onEvent(event)
        }
    }
}
