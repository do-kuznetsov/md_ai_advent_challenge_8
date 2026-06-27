package com.sibgear.deepseek.settings.ui.external.model

data class SettingsViewState(
    val isSettingsDialogOpen: Boolean = false,
    val isProfileDialogOpen: Boolean = false,
    val profileDraft: String = "",
    val isProfileSaving: Boolean = false,
    val isProfileInterviewActive: Boolean = false,
    val profileInterviewQuestionIndex: Int = 0,
    val profileInterviewAnswers: List<String> = emptyList(),
    val profileInterviewAnswerInput: String = "",
    val isProfileInterviewLoading: Boolean = false,
    val profileError: String? = null,
    val isInvariantsDialogOpen: Boolean = false,
    val invariantsDraft: String = "",
    val isInvariantsSaving: Boolean = false,
    val invariantsChatMessages: List<InvariantsChatMessage> = emptyList(),
    val invariantsChatInput: String = "",
    val isInvariantsApplying: Boolean = false,
    val invariantsError: String? = null,
) {
    val isProfileActionEnabled: Boolean
        get() = !isProfileSaving && !isProfileInterviewLoading

    val isInvariantsActionEnabled: Boolean
        get() = !isInvariantsSaving && !isInvariantsApplying
}
