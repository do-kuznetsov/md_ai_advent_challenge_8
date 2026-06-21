package com.sibgear.deepseek.assistant.memory.domain.model

data class InvariantCollectionMessage(
    val role: InvariantCollectionRole,
    val text: String,
)

enum class InvariantCollectionRole {
    Assistant,
    User,
}
