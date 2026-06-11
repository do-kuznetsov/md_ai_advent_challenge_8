package com.sibgear.deepseek

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.sibgear.deepseek.chat.ui.external.shortcut.EditShortcutDispatcher
import com.sibgear.deepseek.chat.ui.external.shortcut.LocalEditShortcutDispatcher
import com.sibgear.deepseek.chat.ui.external.shortcut.rememberEditShortcutDispatcher
import java.awt.Toolkit
import java.awt.event.KeyEvent
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.KeyStroke

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        state = WindowState(width = 1360.dp, height = 765.dp),
        title = "AI Clients",
    ) {
        val editShortcutDispatcher = rememberEditShortcutDispatcher()

        InstallEditMenuBar(editShortcutDispatcher)

        CompositionLocalProvider(LocalEditShortcutDispatcher provides editShortcutDispatcher) {
            App()
        }
    }
}

@Composable
private fun FrameWindowScope.InstallEditMenuBar(dispatcher: EditShortcutDispatcher) {
    DisposableEffect(window, dispatcher) {
        val previousMenuBar = window.jMenuBar
        window.jMenuBar = JMenuBar().apply {
            add(
                JMenu("Edit").apply {
                    addEditItem("Copy", KeyEvent.VK_C, dispatcher::copy)
                    addEditItem("Paste", KeyEvent.VK_V, dispatcher::paste)
                    addEditItem("Cut", KeyEvent.VK_X, dispatcher::cut)
                    addEditItem("Select All", KeyEvent.VK_A, dispatcher::selectAll)
                },
            )
        }

        onDispose {
            window.jMenuBar = previousMenuBar
        }
    }
}

private fun JMenu.addEditItem(
    title: String,
    keyCode: Int,
    action: () -> Unit,
) {
    add(
        JMenuItem(title).apply {
            accelerator = KeyStroke.getKeyStroke(keyCode, MenuShortcutMask)
            addActionListener { action() }
        },
    )
}

private val MenuShortcutMask: Int
    get() = Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
