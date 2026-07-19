package com.sibgear.deepseek.settings.ui.external.view

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sibgear.deepseek.settings.ui.external.model.McpHeaderUiModel
import com.sibgear.deepseek.settings.ui.external.model.McpServerDraft
import com.sibgear.deepseek.settings.ui.external.model.McpServerUiModel
import com.sibgear.deepseek.settings.ui.external.model.SettingsEvent
import com.sibgear.deepseek.settings.ui.external.model.SettingsViewState
import com.sibgear.deepseek.settings.ui.internal.view.ProjectInvariantsDialog
import com.sibgear.deepseek.settings.ui.internal.view.UserProfileDialog

@Composable
fun SettingsDialogs(
    state: SettingsViewState,
    onEvent: (SettingsEvent) -> Unit,
) {
    if (state.isSettingsDialogOpen) {
        SettingsDialog(
            onDismissRequest = { onEvent(SettingsEvent.SettingsDialogClosed) },
            onProfileClicked = { onEvent(SettingsEvent.ProfileDialogOpened) },
            onInvariantsClicked = { onEvent(SettingsEvent.InvariantsDialogOpened) },
            onMcpServersClicked = { onEvent(SettingsEvent.McpServersDialogOpened) },
        )
    }

    if (state.isProfileDialogOpen) {
        UserProfileDialog(
            profileDraft = state.profileDraft,
            isActionEnabled = state.isProfileActionEnabled,
            isInterviewActive = state.isProfileInterviewActive,
            interviewQuestionIndex = state.profileInterviewQuestionIndex,
            interviewAnswerInput = state.profileInterviewAnswerInput,
            isInterviewLoading = state.isProfileInterviewLoading,
            error = state.profileError,
            onDismissRequest = { onEvent(SettingsEvent.ProfileDialogClosed) },
            onProfileChanged = { onEvent(SettingsEvent.ProfileDraftChanged(it)) },
            onSaveClicked = { onEvent(SettingsEvent.ProfileSaved) },
            onInterviewClicked = { onEvent(SettingsEvent.ProfileInterviewStarted) },
            onInterviewAnswerChanged = { onEvent(SettingsEvent.ProfileInterviewAnswerChanged(it)) },
            onInterviewAnswerSubmitted = { onEvent(SettingsEvent.ProfileInterviewAnswerSubmitted) },
        )
    }

    if (state.isInvariantsDialogOpen) {
        ProjectInvariantsDialog(
            invariantsDraft = state.invariantsDraft,
            isActionEnabled = state.isInvariantsActionEnabled,
            chatMessages = state.invariantsChatMessages,
            chatInput = state.invariantsChatInput,
            isApplying = state.isInvariantsApplying,
            error = state.invariantsError,
            onDismissRequest = { onEvent(SettingsEvent.InvariantsDialogClosed) },
            onInvariantsChanged = { onEvent(SettingsEvent.InvariantsDraftChanged(it)) },
            onSaveClicked = { onEvent(SettingsEvent.InvariantsSaved) },
            onApplyClicked = { onEvent(SettingsEvent.InvariantsApplied) },
            onChatInputChanged = { onEvent(SettingsEvent.InvariantsChatInputChanged(it)) },
            onChatMessageSent = { onEvent(SettingsEvent.InvariantsChatMessageSent) },
        )
    }

    if (state.isMcpServersDialogOpen) {
        McpServersDialog(
            servers = state.mcpServers,
            onDismissRequest = { onEvent(SettingsEvent.McpServersDialogClosed) },
            onAddClicked = { onEvent(SettingsEvent.McpServerAddClicked) },
            onEditClicked = { onEvent(SettingsEvent.McpServerEditClicked(it)) },
            onEnabledChanged = { id, isEnabled -> onEvent(SettingsEvent.McpServerEnabledChanged(id, isEnabled)) },
        )
    }

    if (state.isMcpServerFormDialogOpen) {
        McpServerFormDialog(
            draft = state.mcpServerDraft,
            isSaveEnabled = state.isMcpServerSaveEnabled,
            onDismissRequest = { onEvent(SettingsEvent.McpServerFormClosed) },
            onNameChanged = { onEvent(SettingsEvent.McpServerDraftNameChanged(it)) },
            onUrlChanged = { onEvent(SettingsEvent.McpServerDraftUrlChanged(it)) },
            onHeaderAdded = { onEvent(SettingsEvent.McpServerHeaderAdded) },
            onHeaderRemoved = { onEvent(SettingsEvent.McpServerHeaderRemoved(it)) },
            onHeaderNameChanged = { index, text -> onEvent(SettingsEvent.McpServerHeaderNameChanged(index, text)) },
            onHeaderValueChanged = { index, text -> onEvent(SettingsEvent.McpServerHeaderValueChanged(index, text)) },
            onSkipTlsVerificationChanged = {
                onEvent(SettingsEvent.McpServerSkipTlsVerificationChanged(it))
            },
            onSaveClicked = { onEvent(SettingsEvent.McpServerSaved) },
            onUninstallClicked = { onEvent(SettingsEvent.McpServerUninstalled) },
        )
    }
}

@Composable
private fun SettingsDialog(
    onDismissRequest: () -> Unit,
    onProfileClicked: () -> Unit,
    onInvariantsClicked: () -> Unit,
    onMcpServersClicked: () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier.widthIn(min = 320.dp, max = 420.dp),
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Настройки",
                    style = MaterialTheme.typography.titleMedium,
                )

                SettingsActionButton(
                    text = "Профиль",
                    icon = { Icon(imageVector = Icons.Default.Person, contentDescription = null) },
                    onClick = onProfileClicked,
                )

                SettingsActionButton(
                    text = "Правила проекта",
                    icon = { Icon(imageVector = Icons.Default.Lock, contentDescription = null) },
                    onClick = onInvariantsClicked,
                )

                SettingsActionButton(
                    text = "MCP сервера",
                    icon = { Icon(imageVector = Icons.Default.Dns, contentDescription = null) },
                    onClick = onMcpServersClicked,
                )
            }
        }
    }
}

@Composable
private fun McpServersDialog(
    servers: List<McpServerUiModel>,
    onDismissRequest: () -> Unit,
    onAddClicked: () -> Unit,
    onEditClicked: (Int) -> Unit,
    onEnabledChanged: (Int, Boolean) -> Unit,
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "MCP серверы",
                        style = MaterialTheme.typography.titleMedium,
                    )

                    Button(onClick = onAddClicked) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Добавить сервер",
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 2.dp,
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    ),
                ) {
                    if (servers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 104.dp)
                                .padding(20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "MCP серверы пока не добавлены",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            servers.forEachIndexed { index, server ->
                                McpServerRow(
                                    server = server,
                                    showDivider = index < servers.lastIndex,
                                    onEditClicked = { onEditClicked(server.id) },
                                    onEnabledChanged = { onEnabledChanged(server.id, it) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun McpServerRow(
    server: McpServerUiModel,
    showDivider: Boolean,
    onEditClicked: () -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = server.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            IconButton(onClick = onEditClicked) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Редактировать MCP сервер",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Switch(
                checked = server.isEnabled,
                onCheckedChange = onEnabledChanged,
            )
        }

        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 1.dp, max = 1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

@Composable
private fun McpServerFormDialog(
    draft: McpServerDraft,
    isSaveEnabled: Boolean,
    onDismissRequest: () -> Unit,
    onNameChanged: (String) -> Unit,
    onUrlChanged: (String) -> Unit,
    onHeaderAdded: () -> Unit,
    onHeaderRemoved: (Int) -> Unit,
    onHeaderNameChanged: (Int, String) -> Unit,
    onHeaderValueChanged: (Int, String) -> Unit,
    onSkipTlsVerificationChanged: (Boolean) -> Unit,
    onSaveClicked: () -> Unit,
    onUninstallClicked: () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier.widthIn(min = 720.dp, max = 980.dp),
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                McpServerFormHeader(
                    draft = draft,
                    onUninstallClicked = onUninstallClicked,
                )

                if (draft.isNew) {
                    McpServerInputBlock(
                        label = "Название",
                        value = draft.name,
                        placeholder = "MCP server name",
                        onValueChange = onNameChanged,
                    )

                    StreamableHttpBadge()
                } else {
                    Text(
                        text = "Если нужно сменить тип MCP сервера, сначала удалите его.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                McpServerInputBlock(
                    label = "URL",
                    value = draft.url,
                    placeholder = "https://mcp.example.com/mcp",
                    onValueChange = onUrlChanged,
                )

                McpHeadersBlock(
                    headers = draft.headers,
                    onHeaderAdded = onHeaderAdded,
                    onHeaderRemoved = onHeaderRemoved,
                    onHeaderNameChanged = onHeaderNameChanged,
                    onHeaderValueChanged = onHeaderValueChanged,
                )

                McpTlsBlock(
                    skipTlsVerification = draft.skipTlsVerification,
                    onSkipTlsVerificationChanged = onSkipTlsVerificationChanged,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = onDismissRequest) {
                        Text("Отмена")
                    }

                    Button(
                        onClick = onSaveClicked,
                        enabled = isSaveEnabled,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text("Сохранить")
                    }
                }
            }
        }
    }
}

@Composable
private fun McpTlsBlock(
    skipTlsVerification: Boolean,
    onSkipTlsVerificationChanged: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Skip TLS verification",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "Использовать только для доверенных корпоративных MCP endpoints.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Switch(
                checked = skipTlsVerification,
                onCheckedChange = onSkipTlsVerificationChanged,
            )
        }
    }
}

@Composable
private fun McpHeadersBlock(
    headers: List<McpHeaderUiModel>,
    onHeaderAdded: () -> Unit,
    onHeaderRemoved: (Int) -> Unit,
    onHeaderNameChanged: (Int, String) -> Unit,
    onHeaderValueChanged: (Int, String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Headers",
                style = MaterialTheme.typography.titleSmall,
            )

            headers.forEachIndexed { index, header ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = header.name,
                        onValueChange = { onHeaderNameChanged(index, it) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("Header name") },
                    )

                    McpHeaderValueField(
                        value = header.value,
                        onValueChange = { onHeaderValueChanged(index, it) },
                        modifier = Modifier.weight(1f),
                    )

                    IconButton(onClick = { onHeaderRemoved(index) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Удалить header",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onHeaderAdded,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Add header",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun McpServerFormHeader(
    draft: McpServerDraft,
    onUninstallClicked: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (draft.isNew) {
                "Добавить MCP сервер"
            } else {
                "Изменить ${draft.name} MCP"
            },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (!draft.isNew) {
            OutlinedButton(onClick = onUninstallClicked) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = "Удалить",
                    modifier = Modifier.padding(start = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun McpServerInputBlock(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
            )

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(placeholder) },
            )
        }
    }
}

@Composable
private fun McpHeaderValueField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.onFocusChanged { focusState ->
            isFocused = focusState.isFocused
        },
        singleLine = true,
        placeholder = { Text("Header value") },
        visualTransformation = if (isFocused) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation(mask = '*')
        },
    )
}

@Composable
private fun StreamableHttpBadge() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Streamable HTTP",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun SettingsActionButton(
    text: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Text(text = text)
        }
    }
}
