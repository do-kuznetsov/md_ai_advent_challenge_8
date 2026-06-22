package com.sibgear.deepseek.chat.ui.internal.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sibgear.deepseek.chat.domain.model.PromptAttachment
import com.sibgear.deepseek.chat.ui.external.model.ChatEvent
import com.sibgear.deepseek.chat.ui.external.model.ChatViewState
import com.sibgear.deepseek.chat.ui.external.shortcut.EditShortcutEditor
import com.sibgear.deepseek.chat.ui.external.shortcut.LocalEditShortcutDispatcher
import com.sibgear.deepseek.chat.ui.internal.mapper.formatMegabytes

@Composable
internal fun PromptInputArea(
    state: ChatViewState,
    onEvent: (ChatEvent) -> Unit,
    showTaskModeToggle: Boolean = false,
    isTaskModeEnabled: Boolean = false,
    onTaskModeToggled: () -> Unit = {},
    promptHeaderContent: (@Composable () -> Unit)? = null,
    isPromptInputEnabled: Boolean = true,
    isPromptInputLoading: Boolean = false,
) {
    val isLoading = state.isLoading || isPromptInputLoading
    val isSendEnabled = state.prompt.isNotBlank() && !isLoading

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ContextManagementPanel(
            state = state,
            onEvent = onEvent,
        )

        if (promptHeaderContent == null && state.messages.isEmpty() && !state.isSystemPromptReadOnly) {
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
                    enabled = !state.isSystemPromptReadOnly,
                )
            }
        } else if (promptHeaderContent != null) {
            promptHeaderContent()
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            UserPromptInputBox(
                value = state.prompt,
                onValueChange = { onEvent(ChatEvent.PromptChanged(it)) },
                isLoading = isLoading,
                isSendEnabled = isSendEnabled,
                isPromptInputEnabled = isPromptInputEnabled,
                attachment = state.attachment,
                showTaskModeToggle = showTaskModeToggle,
                isTaskModeEnabled = isTaskModeEnabled,
                onTaskModeToggled = onTaskModeToggled,
                onSendClicked = { onEvent(ChatEvent.SendClicked) },
                onAttachClicked = {
                    when (val result = pickTextAttachment()) {
                        is TextAttachmentPickResult.Error -> onEvent(ChatEvent.AttachmentError(result.message))
                        is TextAttachmentPickResult.Selected -> onEvent(ChatEvent.AttachmentSelected(result.attachment))
                        null -> Unit
                    }
                },
                onAttachmentCleared = { onEvent(ChatEvent.AttachmentCleared) },
                modifier = Modifier.fillMaxWidth(),
            )
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

@Composable
private fun UserPromptInputBox(
    value: String,
    onValueChange: (String) -> Unit,
    isLoading: Boolean,
    isSendEnabled: Boolean,
    isPromptInputEnabled: Boolean,
    attachment: PromptAttachment?,
    showTaskModeToggle: Boolean,
    isTaskModeEnabled: Boolean,
    onTaskModeToggled: () -> Unit,
    onSendClicked: () -> Unit,
    onAttachClicked: () -> Unit,
    onAttachmentCleared: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dispatcher = LocalEditShortcutDispatcher.current
    val clipboardManager = LocalClipboardManager.current
    val currentClipboardManager by rememberUpdatedState(clipboardManager)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    var isFocused by remember { mutableStateOf(false) }
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }
    val shape = RoundedCornerShape(4.dp)
    val borderColor = if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    val effectiveSendEnabled = isPromptInputEnabled && isSendEnabled
    val inputBackgroundColor = if (isPromptInputEnabled) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
    }
    val inputTextColor = if (isPromptInputEnabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
    }
    val sendButtonBackground = if (effectiveSendEnabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val sendButtonContentColor = if (effectiveSendEnabled) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }

    LaunchedEffect(value, textFieldValue.text) {
        if (value != textFieldValue.text) {
            textFieldValue = textFieldValue.copy(
                text = value,
                selection = textFieldValue.selection.coerceIn(value),
            )
        }
    }

    fun updateTextFieldValue(nextValue: TextFieldValue) {
        if (!isPromptInputEnabled) {
            return
        }
        textFieldValue = nextValue
        currentOnValueChange(nextValue.text)
    }

    fun copySelection() {
        textFieldValue.selectedText()
            .takeIf { it.isNotEmpty() }
            ?.let { text -> currentClipboardManager.setText(AnnotatedString(text)) }
    }

    fun cutSelection() {
        val selectedText = textFieldValue.selectedText()
        if (selectedText.isNotEmpty()) {
            currentClipboardManager.setText(AnnotatedString(selectedText))
            updateTextFieldValue(textFieldValue.replaceSelection(""))
        }
    }

    fun pasteClipboard() {
        val clipboardText = currentClipboardManager.getText()?.text
        if (clipboardText != null) {
            updateTextFieldValue(textFieldValue.replaceSelection(clipboardText))
        }
    }

    fun selectAllText() {
        updateTextFieldValue(textFieldValue.copy(selection = TextRange(0, textFieldValue.text.length)))
    }

    fun performEditShortcut(shortcut: PromptEditShortcut) {
        when (shortcut) {
            PromptEditShortcut.SelectAll -> selectAllText()
            PromptEditShortcut.Copy -> copySelection()
            PromptEditShortcut.Cut -> cutSelection()
            PromptEditShortcut.Paste -> pasteClipboard()
        }
    }

    val editor = remember {
        object : EditShortcutEditor {
            override fun copy() = copySelection()
            override fun paste() = pasteClipboard()
            override fun cut() = cutSelection()
            override fun selectAll() = selectAllText()
        }
    }

    DisposableEffect(dispatcher, editor) {
        onDispose {
            dispatcher?.unregister(editor)
        }
    }

    Column(
        modifier = modifier
            .height(136.dp)
            .background(inputBackgroundColor, shape)
            .border(1.dp, borderColor, shape),
    ) {
        BasicTextField(
            value = textFieldValue,
            enabled = isPromptInputEnabled,
            onValueChange = { nextValue ->
                updateTextFieldValue(nextValue)
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = 16.dp, top = 12.dp, end = 16.dp)
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                    if (focusState.isFocused) {
                        dispatcher?.register(editor)
                    }
                }
                .onPreviewKeyEvent { event ->
                    val editShortcut = event.promptEditShortcut()
                    when {
                        editShortcut != null -> {
                            performEditShortcut(editShortcut)
                            true
                        }

                        event.isPromptSubmitShortcut() -> {
                            if (effectiveSendEnabled) {
                                onSendClicked()
                            }
                            true
                        }

                        else -> false
                    }
                },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = inputTextColor,
            ),
            minLines = 3,
            maxLines = 3,
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxSize()) {
                    if (textFieldValue.text.isEmpty()) {
                        Text(
                            text = "Введите сообщение",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = if (isPromptInputEnabled) 1f else 0.46f,
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    innerTextField()
                }
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showTaskModeToggle) {
                    IconButton(
                        onClick = onTaskModeToggled,
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (isTaskModeEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                },
                                CircleShape,
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Checklist,
                            contentDescription = "task state machine",
                            modifier = Modifier.size(18.dp),
                            tint = if (isTaskModeEnabled) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                        )
                    }

                    Spacer(modifier = Modifier.width(TaskModeAttachButtonGap))
                }

                IconButton(
                    onClick = onAttachClicked,
                    enabled = isPromptInputEnabled,
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (isPromptInputEnabled) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            CircleShape,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "attach file",
                        modifier = Modifier.size(18.dp),
                        tint = if (isPromptInputEnabled) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        },
                    )
                }

                attachment?.let { selectedAttachment ->
                    Text(
                        text = "${selectedAttachment.fileName} · ${selectedAttachment.sizeBytes.formatMegabytes()}",
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color(0xFF5F6368),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            .clickable(enabled = isPromptInputEnabled) { onAttachmentCleared() },
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

            IconButton(
                onClick = onSendClicked,
                enabled = effectiveSendEnabled,
                modifier = Modifier
                    .size(36.dp)
                    .background(sendButtonBackground, CircleShape),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = sendButtonContentColor,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "send",
                        modifier = Modifier.size(18.dp),
                        tint = sendButtonContentColor,
                    )
                }
            }
        }
    }
}

private enum class PromptEditShortcut(
    val englishKey: Key,
) {
    SelectAll(Key.A),
    Copy(Key.C),
    Cut(Key.X),
    Paste(Key.V),
}

private val TaskModeAttachButtonGap = 12.dp

private fun KeyEvent.promptEditShortcut(): PromptEditShortcut? {
    if (!isPromptCommandKeyDown()) {
        return null
    }

    return PromptEditShortcut.entries.firstOrNull { shortcut -> key == shortcut.englishKey }
}

private fun KeyEvent.isPromptSubmitShortcut(): Boolean =
    key == Key.Enter &&
        type == KeyEventType.KeyDown &&
        (isCtrlPressed || isMetaPressed)

private fun KeyEvent.isPromptCommandKeyDown(): Boolean =
    type == KeyEventType.KeyDown && (isCtrlPressed || isMetaPressed)

private fun TextFieldValue.selectedText(): String {
    val start = minOf(selection.start, selection.end)
    val end = maxOf(selection.start, selection.end)
    return if (start == end) "" else text.substring(start, end)
}

private fun TextFieldValue.replaceSelection(replacement: String): TextFieldValue {
    val start = minOf(selection.start, selection.end)
    val end = maxOf(selection.start, selection.end)
    val nextText = text.replaceRange(start, end, replacement)
    val nextCursor = start + replacement.length
    return copy(
        text = nextText,
        selection = TextRange(nextCursor),
    )
}

private fun TextRange.coerceIn(text: String): TextRange {
    val start = start.coerceIn(0, text.length)
    val end = end.coerceIn(0, text.length)
    return TextRange(start, end)
}
