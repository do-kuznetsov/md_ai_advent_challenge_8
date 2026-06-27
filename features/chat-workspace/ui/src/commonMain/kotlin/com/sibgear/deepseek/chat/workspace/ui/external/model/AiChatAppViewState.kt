package com.sibgear.deepseek.chat.workspace.ui.external.model

data class AiChatAppViewState(
    val tabs: List<ChatTab>,
    val activeTabNumber: Int,
    val selectedStorageType: ChatStorageType,
    val storageDirectoryLabel: String,
    val isStorageMenuExpanded: Boolean = false,
) {
    val activeTab: ChatTab?
        get() = tabs.firstOrNull { it.number == activeTabNumber }

    val isStorageSwitchEnabled: Boolean
        get() = tabs.none { tab ->
            tab.viewModel.state.isLoading ||
                tab.taskSession?.isOrchestratorFsmFlowRunning == true ||
                tab.taskSession?.stageAgents.orEmpty().any { it.viewModel.state.isLoading }
        }

}
