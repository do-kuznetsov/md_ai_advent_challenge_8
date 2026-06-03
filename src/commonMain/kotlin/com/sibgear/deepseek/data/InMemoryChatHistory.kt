package com.sibgear.deepseek.data

import com.sibgear.deepseek.domain.ChatMessage

class InMemoryChatHistory {
    private var messages: List<ChatMessage> = emptyList()

    fun add(message: ChatMessage): List<ChatMessage> {
        messages = messages + message
        return messages
    }

    fun getMessages(): List<ChatMessage> = messages
}
