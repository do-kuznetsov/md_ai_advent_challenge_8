package com.sibgear.deepseek.chat.workspace.ui.external.model

data class StorageSwitchResult(
    val tabs: List<ChatTab>,
    val activeTabNumber: Int,
    val nextTabNumber: Int,
)
