package com.sibgear.deepseek.chat.workspace.ui.external.presentation

import com.sibgear.deepseek.chat.domain.interactor.ChatInteractor
import com.sibgear.deepseek.chat.domain.model.AgentResponse
import com.sibgear.deepseek.chat.domain.model.AiRequestData
import com.sibgear.deepseek.chat.domain.model.ChatMessage
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
    }

    @Test
    fun reviseKeepsCurrentStageAndAcceptsAdditionalInput() = runTest {
        val viewModel = createViewModel(this)
        viewModel.onEvent(AiChatAppEvent.TaskModeToggled)
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.PromptChanged("Implement FSM")))
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.SendClicked))
        advanceUntilIdle()

        viewModel.onEvent(AiChatAppEvent.TaskTransitionRevisionRequested)
        val revisedSession = assertNotNull(viewModel.state.activeTab?.taskSession)
        assertEquals(TaskState.Planning, revisedSession.context?.state)
        assertEquals(TaskExpectedAction.UserPrompt, revisedSession.context?.expectedAction)
        assertEquals(null, revisedSession.pendingTransition)

        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.PromptChanged("Need Kotlin tests")))
        viewModel.onEvent(AiChatAppEvent.ActiveChatEvent(ChatEvent.SendClicked))
        advanceUntilIdle()

        val updatedSession = assertNotNull(viewModel.state.activeTab?.taskSession)
        assertEquals(TaskState.Planning, updatedSession.context?.state)
        assertEquals(TaskState.Execution, updatedSession.pendingTransition?.to)
        assertEquals(1, updatedSession.stageAgents.count { it.session.state == TaskState.Planning })
    }

    private fun createViewModel(scope: CoroutineScope): AiChatAppViewModel {
        val dispatcher = UnconfinedTestDispatcher()
        fun chatViewModel(
            systemPrompt: String = "",
            initialPrompt: String = "",
            isSystemPromptReadOnly: Boolean = false,
        ): ChatViewModel =
            ChatViewModel(
                interactor = ChatInteractor(
                    repository = RoutingAiRepository(
                        chatRepositories = mapOf(
                            com.sibgear.deepseek.chat.domain.model.AiProvider.DeepSeek to FakeAiChatRepository,
                        ),
                        modelRepositories = emptyMap(),
                    ),
                    dispatcher = dispatcher,
                ),
                coroutineScope = scope,
                initialSystemPrompt = systemPrompt,
                initialPrompt = initialPrompt,
                isSystemPromptReadOnly = isSystemPromptReadOnly,
            )

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

    private object FakeAiChatRepository : AiChatRepository {
        override suspend fun sendMessage(request: AiRequestData): AgentResponse =
            AgentResponse(
                messages = listOf(
                    ChatMessage(role = ChatRole.User, content = request.prompt),
                    ChatMessage(role = ChatRole.Assistant, content = "result: ${request.prompt.take(40)}"),
                ),
            )
    }
}
