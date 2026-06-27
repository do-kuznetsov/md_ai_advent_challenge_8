package com.sibgear.deepseek.chat.workspace.ui.external.model

import com.sibgear.deepseek.chat.domain.model.TaskState
import com.sibgear.deepseek.chat.ui.external.model.ChatEvent

sealed interface AiChatAppEvent {
    data object TabAdded : AiChatAppEvent
    data class TabSelected(val number: Int) : AiChatAppEvent
    data class TabClosed(val number: Int) : AiChatAppEvent
    data class StorageMenuExpandedChanged(val isExpanded: Boolean) : AiChatAppEvent
    data class StorageSelected(val storageType: ChatStorageType) : AiChatAppEvent
    data object TaskModeToggled : AiChatAppEvent
    data class TaskStageSelected(val stage: TaskState) : AiChatAppEvent
    data object TaskTransitionAccepted : AiChatAppEvent
    data object TaskStageRejected : AiChatAppEvent
    data class ActiveChatEvent(val event: ChatEvent) : AiChatAppEvent
    data class ActiveTaskStageChatEvent(val event: ChatEvent) : AiChatAppEvent
}
