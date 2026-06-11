package com.sibgear.deepseek.chat.ui.internal.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sibgear.deepseek.chat.ui.external.model.ChatEvent
import com.sibgear.deepseek.chat.ui.external.model.ChatViewState

private val SendButtonWidth = 128.dp
private val SendButtonLoaderColor = Color(0xFF3B82F6)

@Composable
internal fun PromptInputArea(
    state: ChatViewState,
    onEvent: (ChatEvent) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(state.contextUsageLabel)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("system prompt:")

            ShortcutOutlinedTextField(
                value = state.systemPrompt,
                onValueChange = { onEvent(ChatEvent.SystemPromptChanged(it)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )

            Spacer(modifier = Modifier.width(SendButtonWidth))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            ShortcutOutlinedTextField(
                value = state.prompt,
                onValueChange = { onEvent(ChatEvent.PromptChanged(it)) },
                modifier = Modifier
                    .weight(1f)
                    .height(96.dp),
                minLines = 3,
                maxLines = 3,
                placeholder = "Введите сообщение",
                onSubmitShortcut = {
                    if (state.isSendEnabled) {
                        onEvent(ChatEvent.SendClicked)
                    }
                },
            )

            Button(
                onClick = { onEvent(ChatEvent.SendClicked) },
                enabled = state.isSendEnabled,
                modifier = Modifier.width(SendButtonWidth).height(56.dp),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(24.dp).height(24.dp),
                        color = SendButtonLoaderColor,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = "отправить",
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}
