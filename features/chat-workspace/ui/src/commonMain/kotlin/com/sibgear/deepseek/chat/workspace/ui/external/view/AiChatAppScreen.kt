package com.sibgear.deepseek.chat.workspace.ui.external.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sibgear.deepseek.chat.ui.external.view.ChatScreen
import com.sibgear.deepseek.chat.workspace.ui.external.model.AiChatAppEvent
import com.sibgear.deepseek.chat.workspace.ui.external.model.AiChatAppViewState
import com.sibgear.deepseek.chat.workspace.ui.internal.view.ChatTabBar

@Composable
fun AiChatAppScreen(
    state: AiChatAppViewState,
    onEvent: (AiChatAppEvent) -> Unit,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                ChatTabBar(
                    tabs = state.tabs,
                    activeTabNumber = state.activeTabNumber,
                    onTabSelected = { onEvent(AiChatAppEvent.TabSelected(it)) },
                    onTabClosed = { onEvent(AiChatAppEvent.TabClosed(it)) },
                    onTabAdded = { onEvent(AiChatAppEvent.TabAdded) },
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
                )

                state.activeTab?.let { tab ->
                    ChatScreen(
                        state = tab.viewModel.state,
                        onEvent = { onEvent(AiChatAppEvent.ActiveChatEvent(it)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
