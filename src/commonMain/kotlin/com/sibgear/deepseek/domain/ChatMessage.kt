package com.sibgear.deepseek.domain

data class ChatMessage(
    val role: ChatRole,
    val content: String,
)

enum class ChatRole {
    User,
    Assistant,
}
