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
    Completed,
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
    val isReached: Boolean = false,
    val isReadyForTransition: Boolean = false,
)

data class TaskTransitionProposal(
    val from: TaskState,
    val to: TaskState,
    val reason: String,
    val inputForTarget: String,
)

data class TaskSessionSnapshot(
    val isModeEnabled: Boolean = false,
    val context: TaskContext? = null,
    val selectedStage: TaskState = TaskState.Planning,
    val stages: List<TaskStageSession> = emptyList(),
    val pendingTransition: TaskTransitionProposal? = null,
)

class TaskStateMachine {
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

    fun requestRevision(context: TaskContext): TaskContext =
        context.copy(expectedAction = TaskExpectedAction.UserPrompt)
}

fun TaskState.next(): TaskState? =
    TaskState.entries.getOrNull(ordinal + 1)

fun TaskState.previous(): TaskState? =
    TaskState.entries.getOrNull(ordinal - 1)

fun TaskState.isAdjacentOrSame(other: TaskState): Boolean =
    kotlin.math.abs(ordinal - other.ordinal) <= 1
