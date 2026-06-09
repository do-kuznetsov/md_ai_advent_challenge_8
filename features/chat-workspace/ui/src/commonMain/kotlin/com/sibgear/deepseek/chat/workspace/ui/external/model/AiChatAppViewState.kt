package com.sibgear.deepseek.chat.workspace.ui.external.model

data class AiChatAppViewState(
    val tabs: List<ChatTab>,
    val activeTabNumber: Int,
) {
    val activeTab: ChatTab?
        get() = tabs.firstOrNull { it.number == activeTabNumber }
}
