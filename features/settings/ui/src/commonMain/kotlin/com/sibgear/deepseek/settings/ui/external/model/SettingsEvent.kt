package com.sibgear.deepseek.settings.ui.external.model

sealed interface SettingsEvent {
    data object SettingsDialogOpened : SettingsEvent
    data object SettingsDialogClosed : SettingsEvent
    data object ProfileDialogOpened : SettingsEvent
    data object ProfileDialogClosed : SettingsEvent
    data class ProfileDraftChanged(val text: String) : SettingsEvent
    data object ProfileSaved : SettingsEvent
    data object ProfileInterviewStarted : SettingsEvent
    data class ProfileInterviewAnswerChanged(val text: String) : SettingsEvent
    data object ProfileInterviewAnswerSubmitted : SettingsEvent
    data object InvariantsDialogOpened : SettingsEvent
    data object InvariantsDialogClosed : SettingsEvent
    data class InvariantsDraftChanged(val text: String) : SettingsEvent
    data object InvariantsSaved : SettingsEvent
    data class InvariantsChatInputChanged(val text: String) : SettingsEvent
    data object InvariantsChatMessageSent : SettingsEvent
    data object InvariantsApplied : SettingsEvent
    data object McpServersDialogOpened : SettingsEvent
    data object McpServersDialogClosed : SettingsEvent
    data object McpServerAddClicked : SettingsEvent
    data class McpServerEditClicked(val id: Int) : SettingsEvent
    data object McpServerFormClosed : SettingsEvent
    data class McpServerDraftNameChanged(val text: String) : SettingsEvent
    data class McpServerDraftUrlChanged(val text: String) : SettingsEvent
    data object McpServerHeaderAdded : SettingsEvent
    data class McpServerHeaderRemoved(val index: Int) : SettingsEvent
    data class McpServerHeaderNameChanged(val index: Int, val text: String) : SettingsEvent
    data class McpServerHeaderValueChanged(val index: Int, val text: String) : SettingsEvent
    data class McpServerSkipTlsVerificationChanged(val isEnabled: Boolean) : SettingsEvent
    data object McpServerSaved : SettingsEvent
    data object McpServerUninstalled : SettingsEvent
    data class McpServerEnabledChanged(val id: Int, val isEnabled: Boolean) : SettingsEvent
}
