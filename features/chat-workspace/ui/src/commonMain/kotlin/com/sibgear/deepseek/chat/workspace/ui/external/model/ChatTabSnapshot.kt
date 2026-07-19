package com.sibgear.deepseek.chat.workspace.ui.external.model

import com.sibgear.deepseek.chat.domain.model.TaskSessionSnapshot

data class ChatTabSnapshot(
    val number: Int,
    val systemPrompt: String = "",
    val projectPath: String = "",
    val taskSession: TaskSessionSnapshot? = null,
)
