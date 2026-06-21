package com.sibgear.deepseek.chat.workspace.ui.external.model

import com.sibgear.deepseek.chat.ui.external.presentation.ChatViewModel

data class ChatTab(
    val number: Int,
    val title: String,
    val viewModel: ChatViewModel,
    val taskSession: TaskModeSession? = null,
) {
    companion object {
        const val NewTitle = "*NEW"
    }
}
