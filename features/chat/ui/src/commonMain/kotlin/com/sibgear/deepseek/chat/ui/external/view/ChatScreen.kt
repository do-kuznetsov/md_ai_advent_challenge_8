package com.sibgear.deepseek.chat.ui.external.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sibgear.deepseek.chat.ui.external.model.ChatEvent
import com.sibgear.deepseek.chat.ui.external.model.ChatViewState
import com.sibgear.deepseek.chat.ui.internal.view.ApiSettingsPanel
import com.sibgear.deepseek.chat.ui.internal.view.ChatArea
import com.sibgear.deepseek.chat.ui.internal.view.PromptInputArea

@Composable
fun ChatScreen(
    state: ChatViewState,
    onEvent: (ChatEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ChatArea(
                    messages = state.messages,
                    pinnedContextMessageIndex = state.pinnedContextMessageIndex,
                    expandedCompressionMessageIndexes = state.expandedCompressionMessageIndexes,
                    onCompressionSummaryToggled = { onEvent(ChatEvent.CompressionSummaryToggled(it)) },
                    modifier = Modifier.weight(0.8f).fillMaxHeight(),
                )

                ApiSettingsPanel(
                    state = state,
                    onEvent = onEvent,
                    modifier = Modifier.weight(0.2f).fillMaxHeight(),
                )
            }

            PromptInputArea(
                state = state,
                onEvent = onEvent,
            )
        }
    }
}
