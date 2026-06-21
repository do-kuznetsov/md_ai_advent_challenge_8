package com.sibgear.deepseek.chat.workspace.ui.external.presentation

import com.sibgear.deepseek.chat.domain.interactor.ChatInteractor
import com.sibgear.deepseek.chat.domain.model.AgentResponse
import com.sibgear.deepseek.chat.domain.model.AiRequestData
import com.sibgear.deepseek.chat.domain.model.ChatMessage
import com.sibgear.deepseek.chat.domain.model.ChatMessageKind
import com.sibgear.deepseek.chat.domain.model.ChatRole
import com.sibgear.deepseek.chat.domain.model.TaskExpectedAction
import com.sibgear.deepseek.chat.domain.model.TaskState
import com.sibgear.deepseek.chat.domain.repository.AiChatRepository
import com.sibgear.deepseek.chat.domain.repository.RoutingAiRepository
import com.sibgear.deepseek.chat.ui.external.model.ChatEvent
import com.sibgear.deepseek.chat.ui.external.presentation.ChatViewModel
import com.sibgear.deepseek.chat.workspace.ui.external.model.AiChatAppEvent
import com.sibgear.deepseek.chat.workspace.ui.external.model.ChatStorageType
import com.sibgear.deepseek.chat.workspace.ui.external.model.StorageSwitchResult
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
        assertEquals(TaskState.Execution, taskSession.pendingTransition?.to)
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
        assertEquals(TaskState.Validation, taskSession.pendingTransition?.to)
        assertTrue(orchestratorEvents(viewModel).any { it.contains("Transition accepted by user") })
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
        assertEquals(TaskState.Execution, taskSession.pendingTransition?.to)
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
                    it.contains("Current state: planning")
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
        assertEquals(TaskState.Execution, updatedSession.pendingTransition?.to)
        assertEquals(null, updatedSession.pendingRejection)
        assertEquals(1, updatedSession.stageAgents.count { it.session.state == TaskState.Planning })
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
        assertEquals(TaskState.Execution, updatedSession.pendingTransition?.to)
        assertEquals(null, updatedSession.pendingRejection)
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
        assertEquals(TaskState.Execution, updatedSession.pendingTransition?.to)
        assertEquals(null, updatedSession.pendingRejection)
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
        assistantResponse: (AiRequestData) -> String = { request -> "result: ${request.prompt.take(40)}" },
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
                        chatRepositories = mapOf(
                            com.sibgear.deepseek.chat.domain.model.AiProvider.DeepSeek to
                                FakeAiChatRepository(history, assistantResponse),
                        ),
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
            coroutineScope = scope,
            createChatViewModel = { _, _ -> chatViewModel() },
            createTaskStageChatViewModel = { _, _, systemPrompt, initialPrompt ->
                chatViewModel(
                    systemPrompt = systemPrompt,
                    initialPrompt = initialPrompt,
                    isSystemPromptReadOnly = true,
                )
            },
            switchStorage = { _, currentTabs, activeTabNumber, nextTabNumber ->
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
            history += ChatMessage(role = ChatRole.User, content = request.prompt)
            history += ChatMessage(
                role = ChatRole.Assistant,
                content = assistantResponse(request),
            )
            return AgentResponse(messages = history.toList())
        }
    }
}
