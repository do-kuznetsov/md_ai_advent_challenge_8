package com.sibgear.deepseek.chat.workspace.ui.external.model

import com.sibgear.deepseek.chat.domain.model.TaskSessionSnapshot
import com.sibgear.deepseek.chat.domain.model.TaskStageSession
import com.sibgear.deepseek.chat.domain.model.TaskState
import com.sibgear.deepseek.chat.domain.model.TaskTransitionProposal
import com.sibgear.deepseek.chat.ui.external.presentation.ChatViewModel

data class TaskModeSession(
    val isModeEnabled: Boolean = false,
    val context: com.sibgear.deepseek.chat.domain.model.TaskContext? = null,
    val selectedStage: TaskState = TaskState.Planning,
    val stageAgents: List<TaskStageAgent> = emptyList(),
    val pendingTransition: TaskTransitionProposal? = null,
) {
    val selectedStageAgent: TaskStageAgent?
        get() = stageAgents.firstOrNull { it.session.state == selectedStage }

    fun toSnapshot(): TaskSessionSnapshot =
        TaskSessionSnapshot(
            isModeEnabled = isModeEnabled,
            context = context,
            selectedStage = selectedStage,
            stages = stageAgents.map { it.session },
            pendingTransition = pendingTransition,
        )
}

data class TaskStageAgent(
    val session: TaskStageSession,
    val viewModel: ChatViewModel,
)
