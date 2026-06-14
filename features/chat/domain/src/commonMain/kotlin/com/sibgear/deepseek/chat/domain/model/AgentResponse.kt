package com.sibgear.deepseek.chat.domain.model

data class AgentResponse(
    val messages: List<ChatMessage>,
    val stickyFacts: List<StickyFact> = emptyList(),
)
