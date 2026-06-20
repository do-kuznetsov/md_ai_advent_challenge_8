package com.sibgear.deepseek.chat.workspace.ui.external.model

import com.sibgear.deepseek.chat.domain.model.TaskState
import com.sibgear.deepseek.chat.ui.external.model.ChatEvent

sealed interface AiChatAppEvent {
    data object TabAdded : AiChatAppEvent
    data class TabSelected(val number: Int) : AiChatAppEvent
    data class TabClosed(val number: Int) : AiChatAppEvent
    data class StorageMenuExpandedChanged(val isExpanded: Boolean) : AiChatAppEvent
    data class StorageSelected(val storageType: ChatStorageType) : AiChatAppEvent
    data object ProfileDialogOpened : AiChatAppEvent
    data object ProfileDialogClosed : AiChatAppEvent
    data class ProfileDraftChanged(val text: String) : AiChatAppEvent
    data object ProfileSaved : AiChatAppEvent
    data object ProfileInterviewStarted : AiChatAppEvent
    data class ProfileInterviewAnswerChanged(val text: String) : AiChatAppEvent
    data object ProfileInterviewAnswerSubmitted : AiChatAppEvent
    data object TaskModeToggled : AiChatAppEvent
    data class TaskStageSelected(val stage: TaskState) : AiChatAppEvent
    data object TaskTransitionAccepted : AiChatAppEvent
    data object TaskTransitionRevisionRequested : AiChatAppEvent
    data object TaskPreviousStageRequested : AiChatAppEvent
    data class ActiveChatEvent(val event: ChatEvent) : AiChatAppEvent
    data class ActiveTaskStageChatEvent(val event: ChatEvent) : AiChatAppEvent
}
