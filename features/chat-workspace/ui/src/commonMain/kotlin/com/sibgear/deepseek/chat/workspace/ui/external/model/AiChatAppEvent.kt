package com.sibgear.deepseek.chat.workspace.ui.external.model

import com.sibgear.deepseek.chat.ui.external.model.ChatEvent

sealed interface AiChatAppEvent {
    data object TabAdded : AiChatAppEvent
    data class TabSelected(val number: Int) : AiChatAppEvent
    data class TabClosed(val number: Int) : AiChatAppEvent
    data class ActiveChatEvent(val event: ChatEvent) : AiChatAppEvent
}
