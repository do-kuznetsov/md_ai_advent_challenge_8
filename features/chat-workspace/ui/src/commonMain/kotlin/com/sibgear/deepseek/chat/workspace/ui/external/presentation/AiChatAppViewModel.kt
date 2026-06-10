package com.sibgear.deepseek.chat.workspace.ui.external.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sibgear.deepseek.chat.ui.external.model.ChatEvent
import com.sibgear.deepseek.chat.ui.external.presentation.ChatViewModel
import com.sibgear.deepseek.chat.workspace.ui.external.model.AiChatAppEvent
import com.sibgear.deepseek.chat.workspace.ui.external.model.AiChatAppViewState
import com.sibgear.deepseek.chat.workspace.ui.external.model.ChatTab

class AiChatAppViewModel(
    private val createChatViewModel: (tabNumber: Int) -> ChatViewModel,
    initialTabNumbers: List<Int> = emptyList(),
    initialActiveTabNumber: Int? = null,
    initialNextTabNumber: Int? = null,
    private val onWorkspaceChanged: (tabNumbers: List<Int>, activeTabNumber: Int, nextTabNumber: Int) -> Unit = { _, _, _ -> },
) {
    private val initialNumbers = initialTabNumbers
        .filter { it > 0 }
        .distinct()
        .ifEmpty { listOf(1) }
    private var nextTabNumber = maxOf(
        initialNextTabNumber ?: ((initialNumbers.maxOrNull() ?: 0) + 1),
        (initialNumbers.maxOrNull() ?: 0) + 1,
    )

    var state by mutableStateOf(
        createInitialState(
            tabNumbers = initialNumbers,
            activeTabNumber = initialActiveTabNumber,
        ),
    )
        private set

    init {
        notifyWorkspaceChanged()
    }

    fun onEvent(event: AiChatAppEvent) {
        when (event) {
            is AiChatAppEvent.ActiveChatEvent -> handleChatEvent(event.event)
            AiChatAppEvent.TabAdded -> addTab()
            is AiChatAppEvent.TabClosed -> closeTab(event.number)
            is AiChatAppEvent.TabSelected -> {
                if (state.tabs.any { it.number == event.number }) {
                    state = state.copy(activeTabNumber = event.number)
                    notifyWorkspaceChanged()
                }
            }
        }
    }

    private fun createInitialState(
        tabNumbers: List<Int>,
        activeTabNumber: Int?,
    ): AiChatAppViewState {
        val tabs = tabNumbers.map { createTab(it) }
        return AiChatAppViewState(
            tabs = tabs,
            activeTabNumber = activeTabNumber
                ?.takeIf { number -> tabs.any { it.number == number } }
                ?: tabs.first().number,
        )
    }

    private fun createNewTab(): ChatTab {
        val number = nextTabNumber
        nextTabNumber += 1
        return createTab(number)
    }

    private fun createTab(number: Int): ChatTab {
        val viewModel = createChatViewModel(number)
        viewModel.loadModels()

        return ChatTab(
            number = number,
            viewModel = viewModel,
        )
    }

    private fun addTab() {
        val tab = createNewTab()
        state = state.copy(
            tabs = state.tabs + tab,
            activeTabNumber = tab.number,
        )
        notifyWorkspaceChanged()
    }

    private fun closeTab(number: Int) {
        val currentTabs = state.tabs
        val closingIndex = currentTabs.indexOfFirst { it.number == number }
        if (closingIndex == -1) {
            return
        }

        val remainingTabs = currentTabs.filterNot { it.number == number }
        if (remainingTabs.isEmpty()) {
            val replacementTab = createNewTab()
            state = state.copy(
                tabs = listOf(replacementTab),
                activeTabNumber = replacementTab.number,
            )
            notifyWorkspaceChanged()
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
        notifyWorkspaceChanged()
    }

    private fun handleChatEvent(event: ChatEvent) {
        val activeViewModel = state.activeTab?.viewModel ?: return
        when (event) {
            ChatEvent.SendClicked -> activeViewModel.sendPrompt()
            else -> activeViewModel.onEvent(event)
        }
    }

    private fun notifyWorkspaceChanged() {
        onWorkspaceChanged(
            state.tabs.map { it.number },
            state.activeTabNumber,
            nextTabNumber,
        )
    }
}
