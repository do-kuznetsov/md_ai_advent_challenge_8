package com.sibgear.deepseek.ui

sealed interface DeepSeekAppEvent {
    data class ApiKeyInputChanged(val apiKey: String) : DeepSeekAppEvent
    data object ApiKeyConfirmed : DeepSeekAppEvent
    data object TabAdded : DeepSeekAppEvent
    data class TabSelected(val number: Int) : DeepSeekAppEvent
    data class TabClosed(val number: Int) : DeepSeekAppEvent
    data class ActiveChatEvent(val event: DeepSeekViewEvent) : DeepSeekAppEvent
}
