package com.sibgear.deepseek.settings.ui.external.model

data class McpServerUiModel(
    val id: Int,
    val name: String,
    val url: String,
    val isEnabled: Boolean = true,
)

data class McpServerDraft(
    val id: Int? = null,
    val name: String = "",
    val url: String = "",
) {
    val isNew: Boolean
        get() = id == null

    val canSave: Boolean
        get() = name.isNotBlank() && url.isNotBlank()
}
