package com.sibgear.deepseek.ui

data class AiChatAppViewState(
    val tabs: List<ChatTab>,
    val activeTabNumber: Int,
) {
    val activeTab: ChatTab?
        get() = tabs.firstOrNull { it.number == activeTabNumber }
}
