package com.sibgear.deepseek.chat.domain.model

data class AgentResponse(
    val messages: List<ChatMessage>,
    val stickyFacts: List<StickyFact> = emptyList(),
    val branches: List<ChatBranch> = emptyList(),
    val activeBranchId: Int? = null,
)
