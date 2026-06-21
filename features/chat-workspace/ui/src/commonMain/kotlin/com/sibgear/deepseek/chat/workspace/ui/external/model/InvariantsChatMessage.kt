package com.sibgear.deepseek.chat.workspace.ui.external.model

data class InvariantsChatMessage(
    val role: InvariantsChatRole,
    val text: String,
)

enum class InvariantsChatRole {
    Assistant,
    User,
}
