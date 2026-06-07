package com.sibgear.deepseek.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DeepSeekAppScreen(
    state: DeepSeekAppViewState,
    onEvent: (DeepSeekAppEvent) -> Unit,
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
                    onTabSelected = { onEvent(DeepSeekAppEvent.TabSelected(it)) },
                    onTabClosed = { onEvent(DeepSeekAppEvent.TabClosed(it)) },
                    onTabAdded = { onEvent(DeepSeekAppEvent.TabAdded) },
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
                )

                state.activeTab?.let { tab ->
                    DeepSeekScreen(
                        state = tab.viewModel.state,
                        onEvent = { onEvent(DeepSeekAppEvent.ActiveChatEvent(it)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
