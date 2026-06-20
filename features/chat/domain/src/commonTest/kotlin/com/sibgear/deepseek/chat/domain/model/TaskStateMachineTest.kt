package com.sibgear.deepseek.chat.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TaskStateMachineTest {
    private val machine = TaskStateMachine()

    @Test
    fun startsInPlanning() {
        val context = machine.start("Add tests")

        assertEquals(TaskState.Planning, context.state)
        assertEquals(1, context.step)
        assertEquals(4, context.total)
        assertEquals(TaskExpectedAction.AgentWork, context.expectedAction)
    }

    @Test
    fun rejectsSkippedStages() {
        val context = machine.start("Add tests")
            .let { machine.completeStage(it, output = "plan") }

        assertFailsWith<IllegalArgumentException> {
            machine.proposeTransition(
                context = context,
                to = TaskState.Validation,
                reason = "skip",
                inputForTarget = "input",
            )
        }
    }

    @Test
    fun allowsNextAfterCompletedStage() {
        val context = machine.start("Add tests")
            .let { machine.completeStage(it, output = "plan") }
        val proposal = machine.proposeTransition(
            context = context,
            to = TaskState.Execution,
            reason = "plan accepted",
            inputForTarget = "approved plan",
        )

        val next = machine.acceptTransition(context, proposal)

        assertEquals(TaskState.Execution, next.state)
        assertEquals(TaskExpectedAction.AgentWork, next.expectedAction)
    }

    @Test
    fun rejectsNextBeforeStageCompletion() {
        val context = machine.start("Add tests")

        assertFailsWith<IllegalArgumentException> {
            machine.proposeTransition(
                context = context,
                to = TaskState.Execution,
                reason = "too early",
                inputForTarget = "input",
            )
        }
    }

    @Test
    fun allowsPreviousStageProposal() {
        val planning = machine.start("Add tests")
            .let { machine.completeStage(it, output = "plan") }
        val execution = machine.acceptTransition(
            planning,
            machine.proposeTransition(planning, TaskState.Execution, "accepted", "plan"),
        )
        val completedExecution = machine.completeStage(execution, output = "implementation")

        val proposal = machine.proposeTransition(
            context = completedExecution,
            to = TaskState.Planning,
            reason = "need more detail",
            inputForTarget = "feedback",
        )

        assertEquals(TaskState.Planning, proposal.to)
    }

    @Test
    fun doneDoesNotMoveForward() {
        val done = TaskContext(
            task = "Task",
            state = TaskState.Done,
            step = 4,
            total = 4,
            plan = emptyList(),
            done = emptyList(),
            current = "done",
            expectedAction = TaskExpectedAction.Completed,
        )

        assertEquals(null, done.state.next())
    }

    @Test
    fun doneCanReturnToValidationWithProposal() {
        val done = TaskContext(
            task = "Task",
            state = TaskState.Done,
            step = 4,
            total = 4,
            plan = emptyList(),
            done = emptyList(),
            current = "done",
            expectedAction = TaskExpectedAction.Completed,
        )

        val proposal = machine.proposeTransition(
            context = done,
            to = TaskState.Validation,
            reason = "needs validation refinement",
            inputForTarget = "validation input",
        )

        assertEquals(TaskState.Validation, proposal.to)
    }

    @Test
    fun rejectedStageWaitsForOrchestratorDecision() {
        val context = machine.start("Add tests")
            .let { machine.completeStage(it, output = "plan") }

        val rejected = machine.rejectStage(context)

        assertEquals(TaskExpectedAction.OrchestratorDecision, rejected.expectedAction)
    }

    @Test
    fun rejectedStageCanRetryCurrentStage() {
        val context = machine.start("Add tests")
            .let { machine.completeStage(it, output = "plan") }
            .let { machine.acceptTransition(it, machine.proposeTransition(it, TaskState.Execution, "accepted", "plan")) }
            .let { machine.completeStage(it, output = "implementation") }
            .let { machine.rejectStage(it) }

        val resolved = machine.resolveRejectedStage(context, TaskState.Execution)

        assertEquals(TaskState.Execution, resolved.state)
        assertEquals(TaskExpectedAction.AgentWork, resolved.expectedAction)
    }

    @Test
    fun rejectedStageCanReturnPreviousStage() {
        val context = machine.start("Add tests")
            .let { machine.completeStage(it, output = "plan") }
            .let { machine.acceptTransition(it, machine.proposeTransition(it, TaskState.Execution, "accepted", "plan")) }
            .let { machine.completeStage(it, output = "implementation") }
            .let { machine.rejectStage(it) }

        val resolved = machine.resolveRejectedStage(context, TaskState.Planning)

        assertEquals(TaskState.Planning, resolved.state)
        assertEquals(TaskExpectedAction.AgentWork, resolved.expectedAction)
    }

    @Test
    fun rejectedStageCannotMoveForward() {
        val context = machine.start("Add tests")
            .let { machine.completeStage(it, output = "plan") }
            .let { machine.acceptTransition(it, machine.proposeTransition(it, TaskState.Execution, "accepted", "plan")) }
            .let { machine.completeStage(it, output = "implementation") }
            .let { machine.rejectStage(it) }

        assertFailsWith<IllegalArgumentException> {
            machine.resolveRejectedStage(context, TaskState.Validation)
        }
    }

    @Test
    fun askUserAfterRejectionWaitsForUserPrompt() {
        val context = machine.start("Add tests")
            .let { machine.completeStage(it, output = "plan") }
            .let { machine.rejectStage(it) }

        val waiting = machine.awaitRejectionDetails(context)

        assertEquals(TaskExpectedAction.UserPrompt, waiting.expectedAction)
    }
}
