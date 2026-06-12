package com.sibgear.deepseek.chat.ui.internal.view

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
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
import com.sibgear.deepseek.chat.ui.external.shortcut.EditShortcutEditor
import com.sibgear.deepseek.chat.ui.external.shortcut.LocalEditShortcutDispatcher

@Composable
@Suppress("DEPRECATION")
internal fun ShortcutOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
    placeholder: String? = null,
    onSubmitShortcut: (() -> Unit)? = null,
) {
    val dispatcher = LocalEditShortcutDispatcher.current
    val clipboardManager = LocalClipboardManager.current
    val currentClipboardManager by rememberUpdatedState(clipboardManager)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
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
        if (currentEnabled && selectedText.isNotEmpty()) {
            currentClipboardManager.setText(AnnotatedString(selectedText))
            updateTextFieldValue(textFieldValue.replaceSelection(""))
        }
    }

    fun pasteClipboard() {
        val clipboardText = currentClipboardManager.getText()?.text
        if (currentEnabled && clipboardText != null) {
            updateTextFieldValue(textFieldValue.replaceSelection(clipboardText))
        }
    }

    fun selectAllText() {
        updateTextFieldValue(textFieldValue.copy(selection = TextRange(0, textFieldValue.text.length)))
    }

    fun performEditShortcut(shortcut: EditShortcut) {
        when (shortcut) {
            EditShortcut.SelectAll -> selectAllText()
            EditShortcut.Copy -> copySelection()
            EditShortcut.Cut -> cutSelection()
            EditShortcut.Paste -> pasteClipboard()
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

    OutlinedTextField(
        value = textFieldValue,
        onValueChange = { nextValue ->
            updateTextFieldValue(nextValue)
        },
        modifier = modifier
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    dispatcher?.register(editor)
                }
            }
            .onPreviewKeyEvent { event ->
                val editShortcut = event.editShortcut()
                when {
                    editShortcut != null -> {
                        performEditShortcut(editShortcut)
                        true
                    }

                    else -> event.isSubmitShortcut(onSubmitShortcut)
                }
            },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        enabled = enabled,
        placeholder = placeholder?.let { text ->
            { Text(text) }
        },
    )
}

private fun KeyEvent.editShortcut(): EditShortcut? {
    if (!isCommandKeyDown()) {
        return null
    }

    return EditShortcut.entries.firstOrNull { shortcut -> matchesShortcutKey(shortcut) }
}

private fun KeyEvent.matchesShortcutKey(shortcut: EditShortcut): Boolean =
    key == shortcut.englishKey

private enum class EditShortcut(
    val englishKey: Key,
) {
    SelectAll(Key.A),
    Copy(Key.C),
    Cut(Key.X),
    Paste(Key.V),
}

private fun KeyEvent.isSubmitShortcut(onSubmitShortcut: (() -> Unit)?): Boolean {
    val isSubmitShortcut = onSubmitShortcut != null &&
        key == Key.Enter &&
        type == KeyEventType.KeyDown &&
        (isCtrlPressed || isMetaPressed)

    if (isSubmitShortcut) {
        onSubmitShortcut()
    }

    return isSubmitShortcut
}

private fun KeyEvent.isCommandKeyDown(): Boolean =
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
