package com.sibgear.deepseek

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        state = WindowState(width = 1360.dp, height = 765.dp),
        title = "DeepSeek Client",
    ) {
        App()
    }
}
