package com.sibgear.deepseek.settings.ui.external.model

data class InvariantsChatMessage(
    val role: InvariantsChatRole,
    val text: String,
)

enum class InvariantsChatRole {
    Assistant,
    User,
}
