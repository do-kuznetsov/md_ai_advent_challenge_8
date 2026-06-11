package com.sibgear.deepseek.chat.ui.external.shortcut

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

@Stable
class EditShortcutDispatcher internal constructor() {
    private var activeEditor: EditShortcutEditor? by mutableStateOf(null)

    val hasActiveEditor: Boolean
        get() = activeEditor != null

    fun copy() {
        activeEditor?.copy()
    }

    fun paste() {
        activeEditor?.paste()
    }

    fun cut() {
        activeEditor?.cut()
    }

    fun selectAll() {
        activeEditor?.selectAll()
    }

    internal fun register(editor: EditShortcutEditor) {
        activeEditor = editor
    }

    internal fun unregister(editor: EditShortcutEditor) {
        if (activeEditor === editor) {
            activeEditor = null
        }
    }
}

internal interface EditShortcutEditor {
    fun copy()
    fun paste()
    fun cut()
    fun selectAll()
}

val LocalEditShortcutDispatcher = staticCompositionLocalOf<EditShortcutDispatcher?> { null }

@Composable
fun rememberEditShortcutDispatcher(): EditShortcutDispatcher =
    remember { EditShortcutDispatcher() }
