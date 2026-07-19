package com.sibgear.deepseek.chat.workspace.ui.external.presentation

import com.sibgear.deepseek.chat.domain.interactor.ChatInteractor
import com.sibgear.deepseek.chat.domain.model.AgentResponse
import com.sibgear.deepseek.chat.domain.model.AiRequestData
import com.sibgear.deepseek.chat.domain.model.ChatMessage
import com.sibgear.deepseek.chat.domain.model.ChatMessageKind
import com.sibgear.deepseek.chat.domain.model.ChatRole
import com.sibgear.deepseek.chat.domain.model.TaskExpectedAction
import com.sibgear.deepseek.chat.domain.model.TaskStageResultStatus
import com.sibgear.deepseek.chat.domain.model.TaskState
import com.sibgear.deepseek.chat.domain.repository.AiChatRepository
import com.sibgear.deepseek.chat.domain.repository.RoutingAiRepository
import com.sibgear.deepseek.chat.ui.external.model.ChatEvent
import com.sibgear.deepseek.chat.ui.external.presentation.ChatViewModel
import com.sibgear.deepseek.chat.workspace.ui.external.model.AiChatAppEvent
import com.sibgear.deepseek.chat.workspace.ui.external.model.ChatStorageType
import com.sibgear.deepseek.chat.workspace.ui.external.model.StorageSwitchResult
import com.sibgear.deepseek.chat.workspace.ui.external.model.TaskChatFocus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AiChatAppViewModelTaskModeTest {
    @Test
    fun toggleThenNextSendStartsPlanning() = runTest {
        val viewModel = createViewModel(this)

        viewModel.onEvent(AiChatAppEvent.TaskModeToggled)
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.PromptChanged("Implement FSM")))
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.SendClicked))
        advanceUntilIdle()

        val taskSession = assertNotNull(viewModel.state.activeTab?.taskSession)
        assertEquals(TaskState.Planning, taskSession.context?.state)
        assertEquals(TaskState.Planning, taskSession.selectedStage)
        assertTrue(taskSession.stageAgents.any { it.session.state == TaskState.Planning })
        assertEquals(null, taskSession.pendingTransition)
        assertEquals(true, taskSession.stageAgents.first { it.session.state == TaskState.Planning }.session.isReadyForTransition)
        assertEquals(TaskChatFocus.Stage(TaskState.Planning), taskSession.chatFocus)
    }

    @Test
    fun needsUserInputStageResultDoesNotCreateUserDecision() = runTest {
        val viewModel = createViewModel(this) { request ->
            if (request.systemPrompt.contains("Stage result protocol")) {
                """
                [TASK_STAGE_RESULT]
                status: needs_user_input
                output:
                question: Which platform should this target?
                reason: Missing platform requirement
                [/TASK_STAGE_RESULT]
                """.trimIndent()
            } else {
                "result: ${request.prompt.take(80)}"
            }
        }

        viewModel.onEvent(AiChatAppEvent.TaskModeToggled)
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.PromptChanged("Implement FSM")))
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.SendClicked))
        advanceUntilIdle()
        viewModel.onEvent(AiChatAppEvent.TaskTransitionAccepted)
        viewModel.onEvent(AiChatAppEvent.TaskStageRejected)
        advanceUntilIdle()

        val taskSession = assertNotNull(viewModel.state.activeTab?.taskSession)
        val planningAgent = taskSession.stageAgents.first { it.session.state == TaskState.Planning }
        assertEquals(TaskState.Planning, taskSession.context?.state)
        assertEquals(TaskStageResultStatus.NeedsUserInput, planningAgent.session.resultStatus)
        assertEquals("Which platform should this target?", planningAgent.session.resultQuestion)
        assertEquals(null, planningAgent.session.output)
        assertEquals(false, planningAgent.session.isReadyForTransition)
        assertEquals(null, taskSession.pendingTransition)
        assertEquals(null, taskSession.pendingRejection)
        assertEquals(TaskChatFocus.Stage(TaskState.Planning), taskSession.chatFocus)
    }

    @Test
    fun missingStageResultProtocolTriggersSyntheticRepairPrompt() = runTest {
        var sawRepairPrompt = false
        val viewModel = createViewModel(this) { request ->
            when {
                request.systemPrompt.contains("Stage result protocol") &&
                    request.prompt.contains("internal protocol repair request") -> {
                    sawRepairPrompt = true
                    completedStageResult("repaired planning output")
                }

                request.systemPrompt.contains("Stage result protocol") -> "Plain planning output without protocol block."
                else -> "result: ${request.prompt.take(80)}"
            }
        }

        viewModel.onEvent(AiChatAppEvent.TaskModeToggled)
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.PromptChanged("Implement FSM")))
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.SendClicked))
        advanceUntilIdle()

        val taskSession = assertNotNull(viewModel.state.activeTab?.taskSession)
        val planningAgent = taskSession.stageAgents.first { it.session.state == TaskState.Planning }
        assertEquals(true, sawRepairPrompt)
        assertEquals(TaskStageResultStatus.Completed, planningAgent.session.resultStatus)
        assertEquals("repaired planning output", planningAgent.session.output)
        assertEquals(true, planningAgent.session.isReadyForTransition)
    }

    @Test
    fun invalidStageResultProtocolAfterRepairBecomesBlockedResult() = runTest {
        val viewModel = createViewModel(this) { request ->
            if (request.systemPrompt.contains("Stage result protocol")) {
                "Still no valid protocol block."
            } else {
                "result: ${request.prompt.take(80)}"
            }
        }

        viewModel.onEvent(AiChatAppEvent.TaskModeToggled)
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.PromptChanged("Implement FSM")))
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.SendClicked))
        advanceUntilIdle()

        val taskSession = assertNotNull(viewModel.state.activeTab?.taskSession)
        val planningAgent = taskSession.stageAgents.first { it.session.state == TaskState.Planning }
        assertEquals(TaskStageResultStatus.Blocked, planningAgent.session.resultStatus)
        assertTrue(planningAgent.session.output.orEmpty().contains("did not return a valid [TASK_STAGE_RESULT] block"))
        assertTrue(planningAgent.session.resultReason.contains("required stage result protocol"))
        assertEquals(true, planningAgent.session.isReadyForTransition)
    }

    @Test
    fun blockedStageResultAcceptedGoesToOrchestratorAnalysisWithoutForwardTransition() = runTest {
        var rejectionAnalysisPrompt = ""
        val viewModel = createViewModel(this) { request ->
            when {
                request.prompt.contains("TASK_REJECTION_ANALYSIS") -> {
                    rejectionAnalysisPrompt = request.prompt
                    "I need more context."
                }

                request.systemPrompt.contains("Stage result protocol") -> blockedStageResult(
                    output = "Planning cannot continue without repository access.",
                    reason = "Repository access is missing",
                )

                else -> "result: ${request.prompt.take(80)}"
            }
        }

        viewModel.onEvent(AiChatAppEvent.TaskModeToggled)
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.PromptChanged("Implement FSM")))
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.SendClicked))
        advanceUntilIdle()
        viewModel.onEvent(AiChatAppEvent.TaskTransitionAccepted)
        advanceUntilIdle()

        val taskSession = assertNotNull(viewModel.state.activeTab?.taskSession)
        assertEquals(TaskState.Planning, taskSession.context?.state)
        assertEquals(TaskExpectedAction.UserPrompt, taskSession.context?.expectedAction)
        assertEquals(false, taskSession.stageAgents.any { it.session.state == TaskState.Execution })
        assertEquals(TaskChatFocus.Orchestrator, taskSession.chatFocus)
        assertTrue(rejectionAnalysisPrompt.contains("accepted that the planning stage is blocked"))
        assertTrue(rejectionAnalysisPrompt.contains("Blocked stage output"))
        assertTrue(rejectionAnalysisPrompt.contains("Planning cannot continue without repository access."))
        assertTrue(orchestratorEvents(viewModel).any { it.contains("Blocked stage result accepted by user") })
    }

    @Test
    fun acceptTransitionCreatesNextStageAgent() = runTest {
        val viewModel = createViewModel(this)
        viewModel.onEvent(AiChatAppEvent.TaskModeToggled)
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.PromptChanged("Implement FSM")))
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.SendClicked))
        advanceUntilIdle()

        viewModel.onEvent(AiChatAppEvent.TaskTransitionAccepted)
        advanceUntilIdle()

        val taskSession = assertNotNull(viewModel.state.activeTab?.taskSession)
        assertEquals(TaskState.Execution, taskSession.context?.state)
        assertEquals(TaskState.Execution, taskSession.selectedStage)
        assertTrue(taskSession.stageAgents.any { it.session.state == TaskState.Execution })
        assertEquals(null, taskSession.pendingTransition)
        assertEquals(true, taskSession.stageAgents.first { it.session.state == TaskState.Execution }.session.isReadyForTransition)
        assertEquals(TaskChatFocus.Stage(TaskState.Execution), taskSession.chatFocus)
        assertTrue(orchestratorEvents(viewModel).any { it.contains("Stage result accepted by user") })
    }

    @Test
    fun executionStagePromptRequiresActingWithinAcceptedPlanWithoutPreferenceQuestions() = runTest {
        val executionSystemPrompts = mutableListOf<String>()
        val viewModel = createViewModel(this) { request ->
            if (request.systemPrompt.contains("isolated execution agent")) {
                executionSystemPrompts += request.systemPrompt
            }
            if (request.systemPrompt.contains("Stage result protocol")) {
                completedStageResult("result: ${request.prompt.take(80)}")
            } else {
                "result: ${request.prompt.take(80)}"
            }
        }
        viewModel.onEvent(AiChatAppEvent.TaskModeToggled)
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.PromptChanged("Implement CLI Hello world")))
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.SendClicked))
        advanceUntilIdle()

        viewModel.onEvent(AiChatAppEvent.TaskTransitionAccepted)
        advanceUntilIdle()

        val executionPrompt = assertNotNull(executionSystemPrompts.firstOrNull())
        assertTrue(executionPrompt.contains("Do not ask the user to choose how to implement the accepted plan"))
        assertTrue(executionPrompt.contains("Resolve implementation choices yourself within the plan"))
        assertTrue(executionPrompt.contains("Use needs_user_input only for hard blockers"))
        assertTrue(executionPrompt.contains("Do not change, cancel, replace, reorder, skip, or apply Task State Machine stages or transitions"))
    }

    @Test
    fun orchestratorPromptAfterStartStaysInOrchestratorChat() = runTest {
        val viewModel = createViewModel(this)
        viewModel.onEvent(AiChatAppEvent.TaskModeToggled)
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.PromptChanged("Implement FSM")))
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.SendClicked))
        advanceUntilIdle()
        val planningInput = assertNotNull(viewModel.state.activeTab?.taskSession)
            .stageAgents
            .first { it.session.state == TaskState.Planning }
            .session
            .input

        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.PromptChanged("Explain current status")))
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.SendClicked))
        advanceUntilIdle()

        val taskSession = assertNotNull(viewModel.state.activeTab?.taskSession)
        assertEquals(TaskState.Planning, taskSession.context?.state)
        assertEquals(planningInput, taskSession.stageAgents.first { it.session.state == TaskState.Planning }.session.input)
        assertEquals(null, taskSession.pendingTransition)
        assertEquals(TaskChatFocus.Orchestrator, taskSession.chatFocus)
        assertTrue(
            viewModel.state.activeTab
                ?.viewModel
                ?.state
                ?.messages
                .orEmpty()
                .any { it.role == ChatRole.User && it.content == "Explain current status" },
        )
        assertTrue(
            viewModel.state.activeTab
                ?.viewModel
                ?.state
                ?.messages
                .orEmpty()
                .any { it.kind == ChatMessageKind.TaskStateEvent && it.content.contains("Task State Machine started") },
        )
    }

    @Test
    fun everyStageAgentPromptContainsStageBoundaryRules() = runTest {
        val stageSystemPrompts = mutableMapOf<TaskState, String>()
        val viewModel = createViewModel(this) { request ->
            TaskState.entries.firstOrNull { stage ->
                request.systemPrompt.contains("assigned only to the ${stage.title} stage")
            }?.let { stage ->
                stageSystemPrompts[stage] = request.systemPrompt
            }
            if (request.systemPrompt.contains("Stage result protocol")) {
                completedStageResult("result: ${request.prompt.take(80)}")
            } else {
                "result: ${request.prompt.take(80)}"
            }
        }
        viewModel.onEvent(AiChatAppEvent.TaskModeToggled)
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.PromptChanged("Implement FSM")))
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.SendClicked))
        advanceUntilIdle()

        repeat(TaskState.entries.size - 1) {
            viewModel.onEvent(AiChatAppEvent.TaskTransitionAccepted)
            advanceUntilIdle()
        }

        TaskState.entries.forEach { stage ->
            val prompt = assertNotNull(stageSystemPrompts[stage])
            assertTrue(prompt.contains("Stage boundary rules:"))
            assertTrue(prompt.contains("Stay strictly within this stage"))
            assertTrue(prompt.contains("Do not perform deliverables for previous, next, or future stages"))
            assertTrue(prompt.contains("controlled only by the orchestrator and code-owned FSM"))
            assertTrue(prompt.contains("[TASK_STAGE_RESULT]"))
            assertTrue(!prompt.contains("[TASK_ORCHESTRATOR_COMMAND]"))
        }
    }

    @Test
    fun orchestratorPromptInTaskModeReceivesRuntimeBriefing() = runTest {
        val requestSystemPrompts = mutableListOf<String>()
        val viewModel = createViewModel(this) { request ->
            requestSystemPrompts += request.systemPrompt
            "result: ${request.prompt.take(80)}"
        }
        viewModel.onEvent(AiChatAppEvent.TaskModeToggled)
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.PromptChanged("Implement FSM")))
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.SendClicked))
        advanceUntilIdle()

        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.PromptChanged("Explain current status")))
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.SendClicked))
        advanceUntilIdle()

        assertTrue(
            requestSystemPrompts.any {
                it.contains("[TASK_STATE_MACHINE_ORCHESTRATOR_CONTEXT]") &&
                    it.contains("Current state: planning") &&
                    it.contains("Current allowed actions:") &&
                    it.contains("Structured command protocol:") &&
                    it.contains("delegate_current_stage") &&
                    it.contains("Never perform the deliverable") &&
                    it.contains("Do not draft plans, implementation steps, code, validation reports, or final reports yourself") &&
                    it.contains("return delegate_current_stage instead of doing that work") &&
                    it.contains("Do not say that you accepted, rejected, returned, moved")
            },
        )
        assertTrue(
            requestSystemPrompts.any {
                it.contains("isolated planning agent") &&
                    !it.contains("[TASK_STATE_MACHINE_ORCHESTRATOR_CONTEXT]")
            },
        )
    }

    @Test
    fun orchestratorDelegateCommandRunsOnlyCurrentStageAgent() = runTest {
        val viewModel = createViewModel(this) { request ->
            when {
                request.systemPrompt.contains("[TASK_STATE_MACHINE_ORCHESTRATOR_CONTEXT]") -> """
                    I will delegate this to the current stage.

                    [TASK_ORCHESTRATOR_COMMAND]
                    action: delegate_current_stage
                    target_stage: planning
                    reason: User added planning constraints
                    input_for_stage: Delegated planning input with user constraints
                    [/TASK_ORCHESTRATOR_COMMAND]
                """.trimIndent()

                request.systemPrompt.contains("isolated planning agent") &&
                    request.prompt.contains("Delegated planning input") -> completedStageResult("delegated planning output")

                request.systemPrompt.contains("isolated planning agent") -> inProgressStageResult(
                    reason = "Still gathering planning context.",
                )
                else -> "result: ${request.prompt.take(80)}"
            }
        }
        viewModel.onEvent(AiChatAppEvent.TaskModeToggled)
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.PromptChanged("Implement FSM")))
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.SendClicked))
        advanceUntilIdle()

        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.PromptChanged("Add more planning constraints")))
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.SendClicked))
        advanceUntilIdle()

        val taskSession = assertNotNull(viewModel.state.activeTab?.taskSession)
        val planningAgent = taskSession.stageAgents.first { it.session.state == TaskState.Planning }
        assertEquals(TaskState.Planning, taskSession.context?.state)
        assertEquals(null, taskSession.pendingTransition)
        assertEquals("delegated planning output", planningAgent.session.output)
        assertTrue(planningAgent.session.input.contains("Delegated planning input"))
        assertEquals(TaskChatFocus.Stage(TaskState.Planning), taskSession.chatFocus)
        assertTrue(orchestratorEvents(viewModel).any { it.contains("delegated work to the current stage agent") })
    }

    @Test
    fun orchestratorFutureStageCommandIsBlockedByStateMachine() = runTest {
        val viewModel = createViewModel(this) { request ->
            when {
                request.systemPrompt.contains("[TASK_STATE_MACHINE_ORCHESTRATOR_CONTEXT]") -> """
                    I will try to skip planning.

                    [TASK_ORCHESTRATOR_COMMAND]
                    action: delegate_current_stage
                    target_stage: execution
                    reason: User asked to implement immediately
                    input_for_stage: Execute without a completed plan
                    [/TASK_ORCHESTRATOR_COMMAND]
                """.trimIndent()

                request.systemPrompt.contains("isolated planning agent") -> ""
                else -> "result: ${request.prompt.take(80)}"
            }
        }
        viewModel.onEvent(AiChatAppEvent.TaskModeToggled)
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.PromptChanged("Implement FSM")))
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.SendClicked))
        advanceUntilIdle()

        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.PromptChanged("Implement it now")))
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.SendClicked))
        advanceUntilIdle()

        val taskSession = assertNotNull(viewModel.state.activeTab?.taskSession)
        assertEquals(TaskState.Planning, taskSession.context?.state)
        assertEquals(null, taskSession.pendingTransition)
        assertEquals(false, taskSession.stageAgents.any { it.session.state == TaskState.Execution })
        assertEquals(TaskChatFocus.Orchestrator, taskSession.chatFocus)
        assertTrue(orchestratorEvents(viewModel).any { it.contains("command blocked by Task State Machine") })
        assertTrue(orchestratorEvents(viewModel).any { it.contains("current FSM stage is planning") })
    }

    @Test
    fun orchestratorChatAcceptTextIsBlockedAndDoesNotApplyStageResult() = runTest {
        var orchestratorSawAcceptPrompt = false
        var followUpPrompt = ""
        val viewModel = createViewModel(this) { request ->
            when {
                !request.persistUserMessage && request.prompt.contains("Task State Machine result") -> {
                    followUpPrompt = request.prompt
                    "FSM accept command blocked."
                }
                request.systemPrompt.contains("[TASK_STATE_MACHINE_ORCHESTRATOR_CONTEXT]") &&
                    request.prompt == "accept and continue" -> {
                    orchestratorSawAcceptPrompt = true
                    """
                        I am requesting the code-owned FSM to accept the pending transition.

                        [TASK_ORCHESTRATOR_COMMAND]
                        action: accept_current_stage
                        target_stage: execution
                        reason: User asked to accept and continue
                        input_for_stage:
                        user_clarification:
                        [/TASK_ORCHESTRATOR_COMMAND]
                    """.trimIndent()
                }
                else -> "result: ${request.prompt.take(80)}"
            }
        }
        viewModel.onEvent(AiChatAppEvent.TaskModeToggled)
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.PromptChanged("Implement FSM")))
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.SendClicked))
        advanceUntilIdle()

        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.PromptChanged("accept and continue")))
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.SendClicked))
        advanceUntilIdle()

        val taskSession = assertNotNull(viewModel.state.activeTab?.taskSession)
        assertEquals(TaskState.Planning, taskSession.context?.state)
        assertEquals(null, taskSession.pendingTransition)
        assertEquals(false, taskSession.stageAgents.any { it.session.state == TaskState.Execution })
        assertEquals(false, taskSession.isOrchestratorFsmFlowRunning)
        assertTrue(orchestratorSawAcceptPrompt)
        assertTrue(followUpPrompt.contains("action: accept_current_stage"))
        assertTrue(orchestratorEvents(viewModel).any { it.contains("Orchestrator command blocked by Task State Machine") })
    }

    @Test
    fun orchestratorChatRejectTextIsBlockedAndDoesNotRunRejectFlow() = runTest {
        var rejectionAnalysisPrompt = ""
        var orchestratorSawRejectPrompt = false
        var followUpPrompt = ""
        val viewModel = createViewModel(this) { request ->
            when {
                !request.persistUserMessage && request.prompt.contains("Task State Machine result") -> {
                    followUpPrompt = request.prompt
                    "FSM reject command blocked."
                }
                request.prompt.contains("TASK_REJECTION_ANALYSIS") -> {
                    rejectionAnalysisPrompt = request.prompt
                    """
                        TASK_REJECTION_DECISION
                        action: retry_current
                        reason: Tests are required
                        additional_input: Add tests requested by the user
                        question:
                        END_TASK_REJECTION_DECISION
                    """.trimIndent()
                }
                request.systemPrompt.contains("[TASK_STATE_MACHINE_ORCHESTRATOR_CONTEXT]") &&
                    request.prompt == "Отклоняю результат, нужно добавить тесты" -> {
                    orchestratorSawRejectPrompt = true
                    """
                        Запрашиваю у кода отклонение результата этапа.

                        [TASK_ORCHESTRATOR_COMMAND]
                        action: reject_current_stage
                        target_stage: planning
                        reason: User rejected the current stage result
                        input_for_stage:
                        user_clarification: Отклоняю результат, нужно добавить тесты
                        [/TASK_ORCHESTRATOR_COMMAND]
                    """.trimIndent()
                }
                else -> "result: ${request.prompt.take(80)}"
            }
        }
        viewModel.onEvent(AiChatAppEvent.TaskModeToggled)
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.PromptChanged("Implement FSM")))
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.SendClicked))
        advanceUntilIdle()

        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.PromptChanged("Отклоняю результат, нужно добавить тесты")))
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.SendClicked))
        advanceUntilIdle()

        val taskSession = assertNotNull(viewModel.state.activeTab?.taskSession)
        assertEquals(TaskState.Planning, taskSession.context?.state)
        assertEquals(null, taskSession.pendingTransition)
        assertEquals(false, taskSession.isOrchestratorFsmFlowRunning)
        assertTrue(orchestratorSawRejectPrompt)
        assertTrue(followUpPrompt.contains("action: reject_current_stage"))
        assertEquals("", rejectionAnalysisPrompt)
        assertTrue(orchestratorEvents(viewModel).any { it.contains("Orchestrator command blocked by Task State Machine") })
    }

    @Test
    fun orchestratorTransitionCommandIsBlockedAfterLlmRequest() = runTest {
        var orchestratorSawTransitionPrompt = false
        var followUpPrompt = ""
        val viewModel = createViewModel(this) { request ->
            when {
                !request.persistUserMessage && request.prompt.contains("Task State Machine result") -> {
                    followUpPrompt = request.prompt
                    """
                        FSM transition request blocked.

                        [TASK_ORCHESTRATOR_COMMAND]
                        action: accept_current_stage
                        target_stage: execution
                        reason: This follow-up command must be ignored by code
                        input_for_stage:
                        user_clarification:
                        [/TASK_ORCHESTRATOR_COMMAND]
                    """.trimIndent()
                }
                request.systemPrompt.contains("[TASK_STATE_MACHINE_ORCHESTRATOR_CONTEXT]") &&
                    request.prompt == "Вернуться на этап выполнения" -> {
                    orchestratorSawTransitionPrompt = true
                    """
                        Запрашиваю у кода переход состояния.

                        [TASK_ORCHESTRATOR_COMMAND]
                        action: request_stage_transition
                        target_stage: execution
                        reason: User asked to move to execution
                        input_for_stage:
                        user_clarification:
                        [/TASK_ORCHESTRATOR_COMMAND]
                    """.trimIndent()
                }
                else -> "result: ${request.prompt.take(80)}"
            }
        }
        viewModel.onEvent(AiChatAppEvent.TaskModeToggled)
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.PromptChanged("Implement FSM")))
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.SendClicked))
        advanceUntilIdle()

        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.PromptChanged("Вернуться на этап выполнения")))
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.SendClicked))
        advanceUntilIdle()

        val taskSession = assertNotNull(viewModel.state.activeTab?.taskSession)
        assertEquals(TaskState.Planning, taskSession.context?.state)
        assertEquals(null, taskSession.pendingTransition)
        assertEquals(false, taskSession.isOrchestratorFsmFlowRunning)
        assertTrue(orchestratorSawTransitionPrompt)
        assertTrue(followUpPrompt.contains("action: request_stage_transition"))
        assertTrue(orchestratorEvents(viewModel).any { it.contains("Orchestrator command blocked by Task State Machine") })
    }

    @Test
    fun acceptCommandInAgentWorkStateIsBlockedAfterLlmRequest() = runTest {
        var orchestratorSawAcceptPrompt = false
        var followUpPrompt = ""
        val viewModel = createViewModel(this) { request ->
            when {
                !request.persistUserMessage && request.prompt.contains("Task State Machine result") -> {
                    followUpPrompt = request.prompt
                    "FSM accept command blocked."
                }
                request.systemPrompt.contains("[TASK_STATE_MACHINE_ORCHESTRATOR_CONTEXT]") &&
                    request.prompt == "Принимаю" -> {
                    orchestratorSawAcceptPrompt = true
                    """
                        Запрашиваю у кода принятие текущего этапа.

                        [TASK_ORCHESTRATOR_COMMAND]
                        action: accept_current_stage
                        target_stage: planning
                        reason: User said accept
                        input_for_stage:
                        user_clarification:
                        [/TASK_ORCHESTRATOR_COMMAND]
                    """.trimIndent()
                }
                request.systemPrompt.contains("isolated planning agent") -> ""
                else -> "result: ${request.prompt.take(80)}"
            }
        }
        viewModel.onEvent(AiChatAppEvent.TaskModeToggled)
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.PromptChanged("Implement FSM")))
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.SendClicked))
        advanceUntilIdle()

        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.PromptChanged("Принимаю")))
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.SendClicked))
        advanceUntilIdle()

        val taskSession = assertNotNull(viewModel.state.activeTab?.taskSession)
        assertEquals(TaskState.Planning, taskSession.context?.state)
        assertEquals(null, taskSession.pendingTransition)
        assertEquals(false, taskSession.isOrchestratorFsmFlowRunning)
        assertTrue(orchestratorSawAcceptPrompt)
        assertTrue(followUpPrompt.contains("action: accept_current_stage"))
        assertTrue(orchestratorEvents(viewModel).any { it.contains("Orchestrator command blocked by Task State Machine") })
    }

    @Test
    fun activeStagePromptGoesToStageAgent() = runTest {
        val viewModel = createViewModel(this)
        viewModel.onEvent(AiChatAppEvent.TaskModeToggled)
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.PromptChanged("Implement FSM")))
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.SendClicked))
        advanceUntilIdle()

        viewModel.onEvent(AiChatAppEvent.ActiveTaskStageChatEvent(ChatEvent.PromptChanged("Refine this planning result")))
        viewModel.onEvent(AiChatAppEvent.ActiveTaskStageChatEvent(ChatEvent.SendClicked))
        advanceUntilIdle()

        val planningAgent = assertNotNull(viewModel.state.activeTab?.taskSession)
            .stageAgents
            .first { it.session.state == TaskState.Planning }
        assertTrue(planningAgent.session.output.orEmpty().contains("Refine this planning result"))
        assertEquals(TaskChatFocus.Stage(TaskState.Planning), viewModel.state.activeTab?.taskSession?.chatFocus)
        assertTrue(
            planningAgent.viewModel.state.messages.any {
                it.role == ChatRole.User && it.content == "Refine this planning result"
            },
        )
    }

    @Test
    fun inactiveStagePromptEventsAreIgnored() = runTest {
        val viewModel = createViewModel(this)
        viewModel.onEvent(AiChatAppEvent.TaskModeToggled)
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.PromptChanged("Implement FSM")))
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.SendClicked))
        advanceUntilIdle()
        viewModel.onEvent(AiChatAppEvent.TaskTransitionAccepted)
        advanceUntilIdle()
        viewModel.onEvent(AiChatAppEvent.TaskStageSelected(TaskState.Planning))

        val before = assertNotNull(viewModel.state.activeTab?.taskSession)
            .stageAgents
            .first { it.session.state == TaskState.Planning }
            .session
            .output
        viewModel.onEvent(AiChatAppEvent.ActiveTaskStageChatEvent(ChatEvent.PromptChanged("Should be ignored")))
        viewModel.onEvent(AiChatAppEvent.ActiveTaskStageChatEvent(ChatEvent.SendClicked))
        advanceUntilIdle()

        val taskSession = assertNotNull(viewModel.state.activeTab?.taskSession)
        val planningAgent = taskSession.stageAgents.first { it.session.state == TaskState.Planning }
        assertEquals(TaskState.Execution, taskSession.context?.state)
        assertEquals(TaskState.Planning, taskSession.selectedStage)
        assertEquals(TaskChatFocus.Stage(TaskState.Execution), taskSession.chatFocus)
        assertEquals(before, planningAgent.session.output)
        assertEquals("", planningAgent.viewModel.state.prompt)
    }

    @Test
    fun rejectWithRetryCurrentRerunsCurrentStage() = runTest {
        var rejectionAnalysisPrompt = ""
        val viewModel = createViewModel(this) { request ->
            if (request.prompt.contains("TASK_REJECTION_ANALYSIS")) {
                rejectionAnalysisPrompt = request.prompt
                """
                    TASK_REJECTION_DECISION
                    action: retry_current
                    reason: Need more Kotlin test detail
                    additional_input: Add Kotlin tests before moving forward
                    question:
                    END_TASK_REJECTION_DECISION
                """.trimIndent()
            } else if (request.systemPrompt.contains("Stage result protocol")) {
                completedStageResult("result: ${request.prompt.take(80)}")
            } else {
                "result: ${request.prompt.take(80)}"
            }
        }
        viewModel.onEvent(AiChatAppEvent.TaskModeToggled)
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.PromptChanged("Implement FSM")))
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.SendClicked))
        advanceUntilIdle()

        viewModel.onEvent(AiChatAppEvent.TaskStageRejected)
        advanceUntilIdle()

        val updatedSession = assertNotNull(viewModel.state.activeTab?.taskSession)
        assertEquals(TaskState.Planning, updatedSession.context?.state)
        assertEquals(null, updatedSession.pendingTransition)
        assertEquals(null, updatedSession.pendingRejection)
        assertEquals(1, updatedSession.stageAgents.count { it.session.state == TaskState.Planning })
        assertEquals(TaskChatFocus.Stage(TaskState.Planning), updatedSession.chatFocus)
        assertTrue(rejectionAnalysisPrompt.contains("Rejected stage output"))
        assertTrue(orchestratorEvents(viewModel).any { it.contains("retry current stage") })
        assertTrue(
            updatedSession.stageAgents
                .first { it.session.state == TaskState.Planning }
                .session
                .input
                .contains("Add Kotlin tests before moving forward"),
        )
    }

    @Test
    fun rejectWithReturnPreviousMovesBackWithoutUserConfirmation() = runTest {
        val viewModel = createViewModel(this) { request ->
            if (request.prompt.contains("TASK_REJECTION_ANALYSIS")) {
                """
                    TASK_REJECTION_DECISION
                    action: return_previous
                    reason: The implementation exposed a planning gap
                    additional_input: Refine the plan with the rejected execution result
                    question:
                    END_TASK_REJECTION_DECISION
                """.trimIndent()
            } else if (request.systemPrompt.contains("Stage result protocol")) {
                completedStageResult("result: ${request.prompt.take(80)}")
            } else {
                "result: ${request.prompt.take(80)}"
            }
        }
        viewModel.onEvent(AiChatAppEvent.TaskModeToggled)
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.PromptChanged("Implement FSM")))
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.SendClicked))
        advanceUntilIdle()
        viewModel.onEvent(AiChatAppEvent.TaskTransitionAccepted)
        advanceUntilIdle()

        viewModel.onEvent(AiChatAppEvent.TaskStageRejected)
        advanceUntilIdle()

        val updatedSession = assertNotNull(viewModel.state.activeTab?.taskSession)
        assertEquals(TaskState.Planning, updatedSession.context?.state)
        assertEquals(TaskState.Planning, updatedSession.selectedStage)
        assertEquals(null, updatedSession.pendingTransition)
        assertEquals(null, updatedSession.pendingRejection)
        assertEquals(TaskChatFocus.Stage(TaskState.Planning), updatedSession.chatFocus)
        assertTrue(orchestratorEvents(viewModel).any { it.contains("return to previous stage") })
        assertTrue(
            updatedSession.stageAgents
                .first { it.session.state == TaskState.Planning }
                .session
                .input
                .contains("Refine the plan with the rejected execution result"),
        )
    }

    @Test
    fun unrecognizedRejectDecisionAsksUserForClarification() = runTest {
        val viewModel = createViewModel(this) { request ->
            if (request.prompt.contains("TASK_REJECTION_ANALYSIS")) {
                "I need more context."
            } else if (request.systemPrompt.contains("Stage result protocol")) {
                completedStageResult("result: ${request.prompt.take(80)}")
            } else {
                "result: ${request.prompt.take(80)}"
            }
        }
        viewModel.onEvent(AiChatAppEvent.TaskModeToggled)
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.PromptChanged("Implement FSM")))
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.SendClicked))
        advanceUntilIdle()

        viewModel.onEvent(AiChatAppEvent.TaskStageRejected)
        advanceUntilIdle()

        val updatedSession = assertNotNull(viewModel.state.activeTab?.taskSession)
        assertEquals(TaskState.Planning, updatedSession.context?.state)
        assertEquals(TaskExpectedAction.UserPrompt, updatedSession.context?.expectedAction)
        assertEquals(null, updatedSession.pendingTransition)
        assertEquals(TaskChatFocus.Orchestrator, updatedSession.chatFocus)
        assertNotNull(updatedSession.pendingRejection?.question)
        assertTrue(orchestratorEvents(viewModel).any { it.contains("needs user clarification") })
        assertTrue(
            viewModel.state.activeTab
                ?.viewModel
                ?.state
                ?.messages
                .orEmpty()
                .any { it.content.contains("Почему вы отклонили") },
        )
    }

    @Test
    fun userClarificationAfterRejectReturnsToOrchestratorAnalysis() = runTest {
        var rejectionAnalysisCount = 0
        val viewModel = createViewModel(this) { request ->
            if (request.prompt.contains("TASK_REJECTION_ANALYSIS")) {
                rejectionAnalysisCount += 1
                if (rejectionAnalysisCount == 1) {
                    "I need more context."
                } else {
                    """
                        TASK_REJECTION_DECISION
                        action: retry_current
                        reason: User clarified missing validation details
                        additional_input: Add validation details from the user clarification
                        question:
                        END_TASK_REJECTION_DECISION
                    """.trimIndent()
                }
            } else if (request.systemPrompt.contains("Stage result protocol")) {
                completedStageResult("result: ${request.prompt.take(80)}")
            } else {
                "result: ${request.prompt.take(80)}"
            }
        }
        viewModel.onEvent(AiChatAppEvent.TaskModeToggled)
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.PromptChanged("Implement FSM")))
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.SendClicked))
        advanceUntilIdle()
        viewModel.onEvent(AiChatAppEvent.TaskStageRejected)
        advanceUntilIdle()

        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.PromptChanged("The plan missed validation details")))
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.SendClicked))
        advanceUntilIdle()

        val updatedSession = assertNotNull(viewModel.state.activeTab?.taskSession)
        assertEquals(TaskState.Planning, updatedSession.context?.state)
        assertEquals(null, updatedSession.pendingTransition)
        assertEquals(null, updatedSession.pendingRejection)
        assertEquals(TaskChatFocus.Stage(TaskState.Planning), updatedSession.chatFocus)
        assertEquals(2, rejectionAnalysisCount)
        assertTrue(
            updatedSession.stageAgents
                .first { it.session.state == TaskState.Planning }
                .session
                .input
                .contains("Add validation details from the user clarification"),
        )
    }

    private fun orchestratorEvents(viewModel: AiChatAppViewModel): List<String> =
        viewModel.state.activeTab
            ?.viewModel
            ?.state
            ?.messages
            .orEmpty()
            .filter { it.kind == ChatMessageKind.TaskStateEvent }
            .map { it.content }

    private fun createViewModel(
        scope: CoroutineScope,
        assistantResponse: (AiRequestData) -> String = { request ->
            val result = "result: ${request.prompt.take(40)}"
            if (request.systemPrompt.contains("Stage result protocol")) {
                completedStageResult(result)
            } else {
                result
            }
        },
    ): AiChatAppViewModel {
        val dispatcher = UnconfinedTestDispatcher()
        fun chatViewModel(
            systemPrompt: String = "",
            initialPrompt: String = "",
            isSystemPromptReadOnly: Boolean = false,
        ): ChatViewModel {
            val history = mutableListOf<ChatMessage>()
            return ChatViewModel(
                interactor = ChatInteractor(
                    repository = RoutingAiRepository(
                        chatRepositories = com.sibgear.deepseek.chat.domain.model.AiProvider.entries.associateWith {
                            FakeAiChatRepository(history, assistantResponse)
                        },
                        modelRepositories = emptyMap(),
                    ),
                    dispatcher = dispatcher,
                ),
                coroutineScope = scope,
                initialSystemPrompt = systemPrompt,
                initialPrompt = initialPrompt,
                isSystemPromptReadOnly = isSystemPromptReadOnly,
                persistMessage = { message ->
                    history += message
                    history.toList()
                },
            )
        }

        return AiChatAppViewModel(
            createChatViewModel = { _, _, systemPrompt, _ ->
                chatViewModel(systemPrompt = systemPrompt)
            },
            createTaskStageChatViewModel = { _, _, systemPrompt, initialPrompt ->
                chatViewModel(
                    systemPrompt = systemPrompt,
                    initialPrompt = initialPrompt,
                    isSystemPromptReadOnly = true,
                )
            },
            switchStorage = { _, _, currentTabs, activeTabNumber, nextTabNumber ->
                StorageSwitchResult(
                    tabs = currentTabs,
                    activeTabNumber = activeTabNumber,
                    nextTabNumber = nextTabNumber,
                )
            },
            initialTabNumbers = listOf(1),
            initialStorageType = ChatStorageType.Json,
            storageDirectoryLabel = "test",
        )
    }

    private class FakeAiChatRepository(
        private val history: MutableList<ChatMessage>,
        private val assistantResponse: (AiRequestData) -> String,
    ) : AiChatRepository {
        override suspend fun sendMessage(request: AiRequestData): AgentResponse {
            if (request.persistUserMessage) {
                history += ChatMessage(role = ChatRole.User, content = request.prompt)
            }
            history += ChatMessage(
                role = ChatRole.Assistant,
                content = assistantResponse(request),
            )
            return AgentResponse(messages = history.toList())
        }
    }
}

private fun completedStageResult(output: String): String =
    """
    [TASK_STAGE_RESULT]
    status: completed
    output: $output
    question:
    reason:
    [/TASK_STAGE_RESULT]
    """.trimIndent()

private fun inProgressStageResult(reason: String): String =
    """
    [TASK_STAGE_RESULT]
    status: in_progress
    output:
    question:
    reason: $reason
    [/TASK_STAGE_RESULT]
    """.trimIndent()

private fun blockedStageResult(output: String, reason: String): String =
    """
    [TASK_STAGE_RESULT]
    status: blocked
    output: $output
    question:
    reason: $reason
    [/TASK_STAGE_RESULT]
    """.trimIndent()
