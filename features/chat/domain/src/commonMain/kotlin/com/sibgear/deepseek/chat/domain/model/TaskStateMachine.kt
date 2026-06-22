package com.sibgear.deepseek.chat.domain.model

enum class TaskState(
    val title: String,
) {
    Planning("planning"),
    Execution("execution"),
    Validation("validation"),
    Done("done"),
}

enum class TaskExpectedAction {
    UserPrompt,
    AgentWork,
    UserConfirmation,
    OrchestratorDecision,
    Completed,
}

enum class TaskAllowedAction {
    DiscussOnly,
    DelegateCurrentStage,
    WaitStageResult,
    RequireUserConfirmation,
    AnalyzeRejection,
    AskRejectionClarification,
    Completed,
}

enum class TaskStageResultStatus {
    InProgress,
    NeedsUserInput,
    Completed,
    Blocked,
}

data class TaskStageResult(
    val status: TaskStageResultStatus,
    val output: String = "",
    val question: String = "",
    val reason: String = "",
)

data class TaskMachineRuntimeState(
    val isCurrentStageLoading: Boolean = false,
    val hasPendingTransition: Boolean = false,
    val hasPendingRejection: Boolean = false,
)

data class TaskActionAvailability(
    val allowed: Set<TaskAllowedAction>,
    val forbiddenReasons: Map<TaskAllowedAction, String>,
) {
    fun isAllowed(action: TaskAllowedAction): Boolean =
        action in allowed

    fun reasonFor(action: TaskAllowedAction): String? =
        forbiddenReasons[action]
}

data class TaskContext(
    val task: String,
    val state: TaskState,
    val step: Int,
    val total: Int,
    val plan: List<String>,
    val done: List<String>,
    val current: String,
    val expectedAction: TaskExpectedAction,
)

data class TaskStageSession(
    val state: TaskState,
    val chatId: Int,
    val systemPrompt: String,
    val startUserPrompt: String,
    val input: String = "",
    val output: String? = null,
    val resultStatus: TaskStageResultStatus = TaskStageResultStatus.InProgress,
    val resultQuestion: String = "",
    val resultReason: String = "",
    val isReached: Boolean = false,
) {
    val isReadyForTransition: Boolean
        get() = resultStatus == TaskStageResultStatus.Completed ||
            resultStatus == TaskStageResultStatus.Blocked
}

data class TaskTransitionProposal(
    val from: TaskState,
    val to: TaskState,
    val reason: String,
    val inputForTarget: String,
)

data class TaskStageRejection(
    val stage: TaskState,
    val rejectedOutput: String,
    val context: TaskContext,
    val proposedNextStage: TaskState? = null,
    val proposedInputForTarget: String? = null,
    val question: String? = null,
    val reason: String? = null,
)

sealed interface TaskOrchestratorDecision {
    val reason: String

    data class RetryCurrent(
        val additionalInput: String,
        override val reason: String,
    ) : TaskOrchestratorDecision

    data class ReturnPrevious(
        val additionalInput: String,
        override val reason: String,
    ) : TaskOrchestratorDecision

    data class AskUser(
        val question: String,
        override val reason: String,
    ) : TaskOrchestratorDecision
}

data class TaskSessionSnapshot(
    val isModeEnabled: Boolean = false,
    val context: TaskContext? = null,
    val selectedStage: TaskState = TaskState.Planning,
    val stages: List<TaskStageSession> = emptyList(),
    val pendingTransition: TaskTransitionProposal? = null,
    val pendingRejection: TaskStageRejection? = null,
)

class TaskStateMachine {
    fun allowedActions(
        context: TaskContext,
        runtimeState: TaskMachineRuntimeState = TaskMachineRuntimeState(),
    ): TaskActionAvailability {
        val allowed = buildSet {
            add(TaskAllowedAction.DiscussOnly)
            when (context.expectedAction) {
                TaskExpectedAction.AgentWork -> {
                    if (runtimeState.isCurrentStageLoading) {
                        add(TaskAllowedAction.WaitStageResult)
                    } else if (runtimeState.hasPendingTransition) {
                        add(TaskAllowedAction.RequireUserConfirmation)
                    } else {
                        add(TaskAllowedAction.DelegateCurrentStage)
                    }
                }

                TaskExpectedAction.UserConfirmation -> add(TaskAllowedAction.RequireUserConfirmation)
                TaskExpectedAction.OrchestratorDecision -> add(TaskAllowedAction.AnalyzeRejection)
                TaskExpectedAction.UserPrompt -> add(TaskAllowedAction.AskRejectionClarification)
                TaskExpectedAction.Completed -> add(TaskAllowedAction.Completed)
            }
        }
        val forbiddenReasons = TaskAllowedAction.entries
            .filterNot { it in allowed }
            .associateWith { action -> context.forbiddenReason(action, runtimeState) }
        return TaskActionAvailability(
            allowed = allowed,
            forbiddenReasons = forbiddenReasons,
        )
    }

    fun start(task: String): TaskContext =
        TaskContext(
            task = task,
            state = TaskState.Planning,
            step = TaskState.Planning.ordinal + 1,
            total = TaskState.entries.size,
            plan = emptyList(),
            done = emptyList(),
            current = "planning",
            expectedAction = TaskExpectedAction.AgentWork,
        )

    fun completeStage(
        context: TaskContext,
        output: String,
        plan: List<String> = context.plan,
    ): TaskContext {
        val done = if (context.state == TaskState.Planning) {
            context.done
        } else {
            (context.done + output.trim()).filter { it.isNotBlank() }
        }
        return context.copy(
            plan = if (context.state == TaskState.Planning) plan else context.plan,
            done = done,
            expectedAction = if (context.state == TaskState.Done) {
                TaskExpectedAction.Completed
            } else {
                TaskExpectedAction.UserConfirmation
            },
        )
    }

    fun proposeTransition(
        context: TaskContext,
        to: TaskState,
        reason: String,
        inputForTarget: String,
    ): TaskTransitionProposal {
        val isConfirmedDoneBackTransition = context.state == TaskState.Done &&
            context.expectedAction == TaskExpectedAction.Completed &&
            to == context.state.previous()
        require(context.expectedAction == TaskExpectedAction.UserConfirmation || isConfirmedDoneBackTransition) {
            "Transition can be proposed only after a completed stage"
        }
        require(context.state.isAdjacentOrSame(to)) {
            "Only adjacent or repeated stages are allowed: ${context.state} -> $to"
        }
        require(context.state != TaskState.Done || to == TaskState.Done || to == context.state.previous()) {
            "Done does not have a next stage"
        }
        return TaskTransitionProposal(
            from = context.state,
            to = to,
            reason = reason,
            inputForTarget = inputForTarget,
        )
    }

    fun acceptTransition(
        context: TaskContext,
        proposal: TaskTransitionProposal,
    ): TaskContext {
        require(proposal.from == context.state) {
            "Transition proposal belongs to ${proposal.from}, current state is ${context.state}"
        }
        require(context.state.isAdjacentOrSame(proposal.to)) {
            "Only adjacent or repeated stages are allowed: ${context.state} -> ${proposal.to}"
        }
        return context.copy(
            state = proposal.to,
            step = proposal.to.ordinal + 1,
            current = proposal.to.title,
            expectedAction = if (proposal.to == TaskState.Done && proposal.to == context.state) {
                TaskExpectedAction.Completed
            } else {
                TaskExpectedAction.AgentWork
            },
        )
    }

    fun rejectStage(context: TaskContext): TaskContext {
        require(context.expectedAction == TaskExpectedAction.UserConfirmation) {
            "Stage can be rejected only after a completed stage"
        }
        return context.copy(expectedAction = TaskExpectedAction.OrchestratorDecision)
    }

    fun awaitRejectionDetails(context: TaskContext): TaskContext =
        context.copy(expectedAction = TaskExpectedAction.UserPrompt)

    fun resumeRejectionAnalysis(context: TaskContext): TaskContext =
        context.copy(expectedAction = TaskExpectedAction.OrchestratorDecision)

    fun resolveRejectedStage(
        context: TaskContext,
        to: TaskState,
    ): TaskContext {
        require(context.expectedAction == TaskExpectedAction.OrchestratorDecision) {
            "Rejected stage can be resolved only by the orchestrator"
        }
        require(to == context.state || to == context.state.previous()) {
            "Rejected stage can only be retried or returned to the previous stage: ${context.state} -> $to"
        }
        return context.copy(
            state = to,
            step = to.ordinal + 1,
            current = to.title,
            expectedAction = TaskExpectedAction.AgentWork,
        )
    }
}

private fun TaskContext.forbiddenReason(
    action: TaskAllowedAction,
    runtimeState: TaskMachineRuntimeState,
): String =
    when (action) {
        TaskAllowedAction.DiscussOnly -> "Discussion is always allowed."
        TaskAllowedAction.DelegateCurrentStage -> when {
            expectedAction == TaskExpectedAction.UserConfirmation ->
                "Current stage is complete and waiting for explicit user decision: accept or reject."
            expectedAction == TaskExpectedAction.OrchestratorDecision ->
                "Rejected stage is waiting for orchestrator rejection analysis, not ordinary delegation."
            expectedAction == TaskExpectedAction.UserPrompt ->
                "Rejected stage is waiting for user clarification before the orchestrator can choose a stage."
            expectedAction == TaskExpectedAction.Completed ->
                "Task is completed; no further stage delegation is allowed."
            runtimeState.isCurrentStageLoading ->
                "Current stage agent is already working; wait for its result."
            runtimeState.hasPendingTransition ->
                "A completed stage result is waiting for explicit user decision: accept or reject."
            else -> "Current FSM state does not allow stage delegation."
        }

        TaskAllowedAction.WaitStageResult -> "Current stage agent is not running."
        TaskAllowedAction.RequireUserConfirmation -> "No completed stage is waiting for user confirmation."
        TaskAllowedAction.AnalyzeRejection -> "No rejected stage is waiting for orchestrator analysis."
        TaskAllowedAction.AskRejectionClarification -> "No rejected stage is waiting for user clarification."
        TaskAllowedAction.Completed -> "Task is not completed."
    }

fun TaskState.next(): TaskState? =
    TaskState.entries.getOrNull(ordinal + 1)

fun TaskState.previous(): TaskState? =
    TaskState.entries.getOrNull(ordinal - 1)

fun TaskState.isAdjacentOrSame(other: TaskState): Boolean =
    kotlin.math.abs(ordinal - other.ordinal) <= 1
