package com.sibgear.deepseek.chat.ui.internal.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sibgear.deepseek.chat.ui.generated.resources.Res
import com.sibgear.deepseek.chat.ui.generated.resources.ic_paperclip
import com.sibgear.deepseek.chat.ui.external.model.ChatEvent
import com.sibgear.deepseek.chat.ui.external.model.ChatViewState
import com.sibgear.deepseek.chat.ui.internal.mapper.formatMegabytes
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun PromptInputArea(
    state: ChatViewState,
    onEvent: (ChatEvent) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ContextManagementPanel(
            state = state,
            onEvent = onEvent,
        )

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
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
            ) {
                ShortcutOutlinedTextField(
                    value = state.prompt,
                    onValueChange = { onEvent(ChatEvent.PromptChanged(it)) },
                    modifier = Modifier.fillMaxSize(),
                    minLines = 3,
                    maxLines = 3,
                    placeholder = "Введите сообщение",
                    onSubmitShortcut = {
                        if (state.isSendEnabled) {
                            onEvent(ChatEvent.SendClicked)
                        }
                    },
                )

                IconButton(
                    onClick = {
                        when (val result = pickTextAttachment()) {
                            is TextAttachmentPickResult.Error -> onEvent(ChatEvent.AttachmentError(result.message))
                            is TextAttachmentPickResult.Selected -> onEvent(ChatEvent.AttachmentSelected(result.attachment))
                            null -> Unit
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_paperclip),
                        contentDescription = "attach file",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        state.attachment?.let { attachment ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_paperclip),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color(0xFF5F6368),
                )
                Text(
                    text = "${attachment.fileName} · ${attachment.sizeBytes.formatMegabytes()}",
                    color = Color(0xFF5F6368),
                    style = MaterialTheme.typography.labelSmall,
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        .clickable { onEvent(ChatEvent.AttachmentCleared) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "×",
                        color = Color(0xFF5F6368),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        state.attachmentError?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
