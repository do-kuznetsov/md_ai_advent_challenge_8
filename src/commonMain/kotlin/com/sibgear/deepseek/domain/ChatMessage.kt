package com.sibgear.deepseek.domain

data class ChatMessage(
    val role: ChatRole,
    val content: String,
    val sourceLabel: String? = null,
)

enum class ChatRole {
    User,
    Assistant,
}
