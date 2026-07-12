package com.sibgear.deepseek.chat.domain.model

data class StreamingChatDelta(
    val type: StreamingChatDeltaType,
    val text: String,
)

enum class StreamingChatDeltaType {
    Thinking,
    Content,
}
