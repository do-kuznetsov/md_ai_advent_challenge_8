package com.sibgear.deepseek.chat.workspace.ui.external.model

import com.sibgear.deepseek.chat.domain.model.TaskContext
import com.sibgear.deepseek.chat.domain.model.TaskExpectedAction
import com.sibgear.deepseek.chat.domain.model.TaskSessionSnapshot
import com.sibgear.deepseek.chat.domain.model.TaskStageRejection
import com.sibgear.deepseek.chat.domain.model.TaskStageSession
import com.sibgear.deepseek.chat.domain.model.TaskState
import com.sibgear.deepseek.chat.domain.model.TaskTransitionProposal
import com.sibgear.deepseek.chat.ui.external.presentation.ChatViewModel

data class TaskModeSession(
    val isModeEnabled: Boolean = false,
    val context: TaskContext? = null,
    val selectedStage: TaskState = TaskState.Planning,
    val chatFocus: TaskChatFocus = TaskChatFocus.Orchestrator,
    val stageAgents: List<TaskStageAgent> = emptyList(),
    val pendingTransition: TaskTransitionProposal? = null,
    val pendingRejection: TaskStageRejection? = null,
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
            pendingRejection = pendingRejection,
        )
}

data class TaskStageAgent(
    val session: TaskStageSession,
    val viewModel: ChatViewModel,
)

sealed interface TaskChatFocus {
    data object Orchestrator : TaskChatFocus

    data class Stage(
        val stage: TaskState,
    ) : TaskChatFocus
}

fun TaskContext?.defaultTaskChatFocus(): TaskChatFocus {
    val context = this ?: return TaskChatFocus.Orchestrator
    return when (context.expectedAction) {
        TaskExpectedAction.AgentWork,
        TaskExpectedAction.UserConfirmation,
        TaskExpectedAction.Completed -> TaskChatFocus.Stage(context.state)
        TaskExpectedAction.UserPrompt,
        TaskExpectedAction.OrchestratorDecision -> TaskChatFocus.Orchestrator
    }
}
