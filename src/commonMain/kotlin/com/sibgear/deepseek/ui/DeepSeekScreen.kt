package com.sibgear.deepseek.ui

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

@Composable
fun DeepSeekScreen(
    state: DeepSeekViewState,
    onEvent: (DeepSeekViewEvent) -> Unit,
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
