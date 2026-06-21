package com.sibgear.deepseek.chat.workspace.ui.internal.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sibgear.deepseek.chat.workspace.ui.external.model.InvariantsChatMessage
import com.sibgear.deepseek.chat.workspace.ui.external.model.InvariantsChatRole

@Composable
internal fun ProjectInvariantsDialog(
    invariantsDraft: String,
    isActionEnabled: Boolean,
    chatMessages: List<InvariantsChatMessage>,
    chatInput: String,
    isApplying: Boolean,
    error: String?,
    onDismissRequest: () -> Unit,
    onInvariantsChanged: (String) -> Unit,
    onSaveClicked: () -> Unit,
    onApplyClicked: () -> Unit,
    onChatInputChanged: (String) -> Unit,
    onChatMessageSent: () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier.widthIn(min = 720.dp, max = 980.dp),
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Инварианты проекта",
                    style = MaterialTheme.typography.titleMedium,
                )

                OutlinedTextField(
                    value = invariantsDraft,
                    onValueChange = onInvariantsChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 340.dp),
                    enabled = isActionEnabled,
                    minLines = 10,
                    maxLines = 16,
                )

                InvariantsChatBlock(
                    messages = chatMessages,
                    input = chatInput,
                    isApplying = isApplying,
                    isActionEnabled = isActionEnabled,
                    onInputChanged = onChatInputChanged,
                    onMessageSent = onChatMessageSent,
                )

                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onApplyClicked,
                        enabled = isActionEnabled,
                    ) {
                        Text("применить")
                    }

                    Button(
                        onClick = onSaveClicked,
                        enabled = isActionEnabled,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text("сохранить")
                    }
                }
            }
        }
    }
}

@Composable
private fun InvariantsChatBlock(
    messages: List<InvariantsChatMessage>,
    input: String,
    isApplying: Boolean,
    isActionEnabled: Boolean,
    onInputChanged: (String) -> Unit,
    onMessageSent: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp, max = 240.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            messages.forEach { message ->
                InvariantsChatBubble(message)
            }
        }

        if (isApplying) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    text = "обновляю инварианты",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp, max = 140.dp),
                enabled = isActionEnabled,
                minLines = 2,
                maxLines = 5,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onMessageSent,
                    enabled = isActionEnabled && input.isNotBlank(),
                ) {
                    Text("отправить")
                }
            }
        }
    }
}

@Composable
private fun InvariantsChatBubble(message: InvariantsChatMessage) {
    val alignment = when (message.role) {
        InvariantsChatRole.Assistant -> Alignment.CenterStart
        InvariantsChatRole.User -> Alignment.CenterEnd
    }
    val backgroundColor = when (message.role) {
        InvariantsChatRole.Assistant -> MaterialTheme.colorScheme.surface
        InvariantsChatRole.User -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = when (message.role) {
        InvariantsChatRole.Assistant -> MaterialTheme.colorScheme.onSurface
        InvariantsChatRole.User -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.86f),
            color = backgroundColor,
            contentColor = contentColor,
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
