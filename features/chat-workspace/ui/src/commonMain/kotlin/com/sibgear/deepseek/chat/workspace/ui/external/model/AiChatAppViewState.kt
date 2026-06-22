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
    val isInvariantsDialogOpen: Boolean = false,
    val invariantsDraft: String = "",
    val isInvariantsSaving: Boolean = false,
    val invariantsChatMessages: List<InvariantsChatMessage> = emptyList(),
    val invariantsChatInput: String = "",
    val isInvariantsApplying: Boolean = false,
    val invariantsError: String? = null,
) {
    val activeTab: ChatTab?
        get() = tabs.firstOrNull { it.number == activeTabNumber }

    val isStorageSwitchEnabled: Boolean
        get() = tabs.none { tab ->
            tab.viewModel.state.isLoading ||
                tab.taskSession?.isOrchestratorFsmFlowRunning == true ||
                tab.taskSession?.stageAgents.orEmpty().any { it.viewModel.state.isLoading }
        }

    val isProfileActionEnabled: Boolean
        get() = !isProfileSaving && !isProfileInterviewLoading

    val isInvariantsActionEnabled: Boolean
        get() = !isInvariantsSaving && !isInvariantsApplying
}
