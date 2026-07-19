package com.sibgear.deepseek.settings.ui.external.model

data class McpHeaderUiModel(
    val name: String,
    val value: String,
)

data class McpServerUiModel(
    val id: Int,
    val name: String,
    val url: String,
    val isEnabled: Boolean = true,
    val headers: List<McpHeaderUiModel> = emptyList(),
    val skipTlsVerification: Boolean = false,
)

data class McpServerDraft(
    val id: Int? = null,
    val name: String = "",
    val url: String = "",
    val headers: List<McpHeaderUiModel> = emptyList(),
    val skipTlsVerification: Boolean = false,
) {
    val isNew: Boolean
        get() = id == null

    val canSave: Boolean
        get() = name.isNotBlank() && url.isNotBlank()

    val sanitizedHeaders: List<McpHeaderUiModel>
        get() = headers.sanitizedMcpHeaders()
}

fun List<McpServerUiModel>.sanitizedMcpServers(): List<McpServerUiModel> =
    filter { server -> server.id > 0 && server.name.isNotBlank() && server.url.isNotBlank() }
        .distinctBy { it.id }
        .map { server ->
            server.copy(
                name = server.name.trim(),
                url = server.url.trim(),
                headers = server.headers.sanitizedMcpHeaders(),
            )
        }

fun List<McpHeaderUiModel>.sanitizedMcpHeaders(): List<McpHeaderUiModel> =
    map { header ->
        McpHeaderUiModel(
            name = header.name.trim(),
            value = header.value.trim(),
        )
    }
        .filter { header -> header.name.isNotBlank() && header.value.isNotBlank() }
        .asReversed()
        .distinctBy { header -> header.name }
        .asReversed()
