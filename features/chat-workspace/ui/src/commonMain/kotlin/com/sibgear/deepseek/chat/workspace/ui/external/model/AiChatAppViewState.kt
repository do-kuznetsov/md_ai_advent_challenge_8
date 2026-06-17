package com.sibgear.deepseek.chat.workspace.ui.external.model

data class AiChatAppViewState(
    val tabs: List<ChatTab>,
    val activeTabNumber: Int,
    val selectedStorageType: ChatStorageType,
    val storageDirectoryLabel: String,
    val isStorageMenuExpanded: Boolean = false,
    val isProfileDialogOpen: Boolean = false,
    val profileDraft: String = "",
    val isProfileSaving: Boolean = false,
    val isProfileInterviewActive: Boolean = false,
    val profileInterviewQuestionIndex: Int = 0,
    val profileInterviewAnswers: List<String> = emptyList(),
    val profileInterviewAnswerInput: String = "",
    val isProfileInterviewLoading: Boolean = false,
    val profileError: String? = null,
) {
    val activeTab: ChatTab?
        get() = tabs.firstOrNull { it.number == activeTabNumber }

    val isStorageSwitchEnabled: Boolean
        get() = tabs.none { it.viewModel.state.isLoading }

    val isProfileActionEnabled: Boolean
        get() = !isProfileSaving && !isProfileInterviewLoading
}
