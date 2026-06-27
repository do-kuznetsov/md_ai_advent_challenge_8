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
}
