package com.sibgear.deepseek.chat.workspace.ui.external.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sibgear.deepseek.assistant.memory.domain.model.AssistantInvariant
import com.sibgear.deepseek.assistant.memory.domain.model.InvariantCategory
import com.sibgear.deepseek.assistant.memory.domain.model.InvariantCollectionMessage
import com.sibgear.deepseek.assistant.memory.domain.model.InvariantCollectionRole
import com.sibgear.deepseek.chat.domain.model.ChatMessage
import com.sibgear.deepseek.chat.domain.model.ChatMessageKind
import com.sibgear.deepseek.chat.domain.model.ChatRole
import com.sibgear.deepseek.chat.domain.model.TaskActionAvailability
import com.sibgear.deepseek.chat.domain.model.TaskAllowedAction
import com.sibgear.deepseek.chat.domain.model.TaskContext
import com.sibgear.deepseek.chat.domain.model.TaskExpectedAction
import com.sibgear.deepseek.chat.domain.model.TaskMachineRuntimeState
import com.sibgear.deepseek.chat.domain.model.TaskOrchestratorDecision
import com.sibgear.deepseek.chat.domain.model.TaskSessionSnapshot
import com.sibgear.deepseek.chat.domain.model.TaskStageRejection
import com.sibgear.deepseek.chat.domain.model.TaskStageResult
import com.sibgear.deepseek.chat.domain.model.TaskStageResultStatus
import com.sibgear.deepseek.chat.domain.model.TaskStageSession
import com.sibgear.deepseek.chat.domain.model.TaskState
import com.sibgear.deepseek.chat.domain.model.TaskStateMachine
import com.sibgear.deepseek.chat.domain.model.TaskTransitionProposal
import com.sibgear.deepseek.chat.domain.model.next
import com.sibgear.deepseek.chat.domain.model.previous
import com.sibgear.deepseek.chat.ui.external.model.ChatEvent
import com.sibgear.deepseek.chat.ui.external.model.ChatViewState
import com.sibgear.deepseek.chat.ui.external.presentation.ChatViewModel
import com.sibgear.deepseek.chat.workspace.ui.external.model.AiChatAppEvent
import com.sibgear.deepseek.chat.workspace.ui.external.model.AiChatAppViewState
import com.sibgear.deepseek.chat.workspace.ui.external.model.ChatTabSnapshot
import com.sibgear.deepseek.chat.workspace.ui.external.model.ChatStorageType
import com.sibgear.deepseek.chat.workspace.ui.external.model.ChatTab
import com.sibgear.deepseek.chat.workspace.ui.external.model.InvariantsChatMessage
import com.sibgear.deepseek.chat.workspace.ui.external.model.InvariantsChatRole
import com.sibgear.deepseek.chat.workspace.ui.external.model.StorageSwitchResult
import com.sibgear.deepseek.chat.workspace.ui.external.model.TaskChatFocus
import com.sibgear.deepseek.chat.workspace.ui.external.model.TaskModeSession
import com.sibgear.deepseek.chat.workspace.ui.external.model.TaskStageAgent
import com.sibgear.deepseek.chat.workspace.ui.external.model.defaultTaskChatFocus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AiChatAppViewModel(
    private val coroutineScope: CoroutineScope,
    private val createChatViewModel: (
        tabNumber: Int,
        storageType: ChatStorageType,
        systemPrompt: String,
    ) -> ChatViewModel,
    private val createTaskStageChatViewModel: (
        chatId: Int,
        storageType: ChatStorageType,
        systemPrompt: String,
        initialPrompt: String,
    ) -> ChatViewModel,
    private val createInitialTabTitle: (tabNumber: Int) -> String = { ChatTab.NewTitle },
    private val switchStorage: (
        sourceStorageType: ChatStorageType,
        storageType: ChatStorageType,
        currentTabs: List<ChatTab>,
        activeTabNumber: Int,
        nextTabNumber: Int,
    ) -> StorageSwitchResult,
    initialTabNumbers: List<Int> = emptyList(),
    initialTaskSessionsByTab: Map<Int, TaskSessionSnapshot> = emptyMap(),
    initialSystemPromptsByTab: Map<Int, String> = emptyMap(),
    initialActiveTabNumber: Int? = null,
    initialNextTabNumber: Int? = null,
    initialStorageType: ChatStorageType = ChatStorageType.Json,
    private val storageDirectoryLabel: String,
    private val onWorkspaceChanged: (
        tabs: List<ChatTabSnapshot>,
        activeTabNumber: Int,
        nextTabNumber: Int,
        storageType: ChatStorageType,
    ) -> Unit = { _, _, _, _ -> },
    private val onTabClosed: (tabNumber: Int, storageType: ChatStorageType) -> Unit = { _, _ -> },
    private val loadProfileAction: suspend (storageType: ChatStorageType) -> String = { "" },
    private val saveProfileAction: suspend (storageType: ChatStorageType, text: String) -> String = { _, text -> text },
    private val updateProfileFromInterviewAction: suspend (
        providerName: String,
        modelId: String,
        currentProfile: String,
        answers: List<String>,
    ) -> String = { _, _, currentProfile, _ -> currentProfile },
    private val loadInvariantsAction: suspend (storageType: ChatStorageType) -> List<AssistantInvariant> = { emptyList() },
    private val saveInvariantsAction: suspend (
        storageType: ChatStorageType,
        invariants: List<AssistantInvariant>,
    ) -> List<AssistantInvariant> = { _, invariants -> invariants },
    private val updateInvariantsFromChatAction: suspend (
        providerName: String,
        modelId: String,
        currentInvariants: List<AssistantInvariant>,
        chatMessages: List<InvariantCollectionMessage>,
    ) -> List<AssistantInvariant> = { _, _, currentInvariants, _ -> currentInvariants },
) {
    private val taskMachine = TaskStateMachine()
    private val initialNumbers = initialTabNumbers
        .filter { it > 0 }
        .distinct()
        .ifEmpty { listOf(1) }
    private val initialTaskSnapshots = initialTaskSessionsByTab
    private val initialSystemPrompts = initialSystemPromptsByTab
    private var nextTabNumber = maxOf(
        initialNextTabNumber ?: ((initialNumbers.maxOrNull() ?: 0) + 1),
        (initialNumbers.maxOrNull() ?: 0) + 1,
    )

    var state by mutableStateOf(
        createInitialState(
            tabNumbers = initialNumbers,
            activeTabNumber = initialActiveTabNumber,
            storageType = initialStorageType,
            taskSnapshotsByTab = initialTaskSnapshots,
            systemPromptsByTab = initialSystemPrompts,
        ),
    )
        private set

    init {
        notifyWorkspaceChanged()
    }

    fun onEvent(event: AiChatAppEvent) {
        when (event) {
            is AiChatAppEvent.ActiveChatEvent -> handleChatEvent(event.event)
            AiChatAppEvent.TabAdded -> addTab()
            is AiChatAppEvent.TabClosed -> closeTab(event.number)
            is AiChatAppEvent.StorageMenuExpandedChanged -> {
                state = state.copy(isStorageMenuExpanded = event.isExpanded)
            }
            is AiChatAppEvent.StorageSelected -> selectStorage(event.storageType)
            AiChatAppEvent.ProfileDialogOpened -> openProfileDialog()
            AiChatAppEvent.ProfileDialogClosed -> closeProfileDialog()
            is AiChatAppEvent.ProfileDraftChanged -> {
                state = state.copy(profileDraft = event.text, profileError = null)
            }
            AiChatAppEvent.ProfileSaved -> saveProfile()
            AiChatAppEvent.ProfileInterviewStarted -> startProfileInterview()
            is AiChatAppEvent.ProfileInterviewAnswerChanged -> {
                state = state.copy(profileInterviewAnswerInput = event.text, profileError = null)
            }
            AiChatAppEvent.ProfileInterviewAnswerSubmitted -> submitProfileInterviewAnswer()
            AiChatAppEvent.InvariantsDialogOpened -> openInvariantsDialog()
            AiChatAppEvent.InvariantsDialogClosed -> closeInvariantsDialog()
            is AiChatAppEvent.InvariantsDraftChanged -> {
                state = state.copy(invariantsDraft = event.text, invariantsError = null)
            }
            AiChatAppEvent.InvariantsSaved -> saveInvariants()
            is AiChatAppEvent.InvariantsChatInputChanged -> {
                state = state.copy(invariantsChatInput = event.text, invariantsError = null)
            }
            AiChatAppEvent.InvariantsChatMessageSent -> sendInvariantsChatMessage()
            AiChatAppEvent.InvariantsApplied -> applyInvariantsChat()
            AiChatAppEvent.TaskModeToggled -> toggleTaskMode()
            is AiChatAppEvent.TaskStageSelected -> selectTaskStage(event.stage)
            AiChatAppEvent.TaskTransitionAccepted -> {
                state.activeTabNumber.takeIf { it > 0 }?.let { tabNumber -> acceptTaskTransition(tabNumber) }
            }
            AiChatAppEvent.TaskStageRejected -> {
                state.activeTabNumber.takeIf { it > 0 }?.let { tabNumber -> rejectTaskStage(tabNumber) }
            }
            is AiChatAppEvent.ActiveTaskStageChatEvent -> handleTaskStageChatEvent(event.event)
            is AiChatAppEvent.TabSelected -> {
                if (state.tabs.any { it.number == event.number }) {
                    state = state.copy(activeTabNumber = event.number)
                    notifyWorkspaceChanged()
                }
            }
        }
    }

    private fun createInitialState(
        tabNumbers: List<Int>,
        activeTabNumber: Int?,
        storageType: ChatStorageType,
        taskSnapshotsByTab: Map<Int, TaskSessionSnapshot>,
        systemPromptsByTab: Map<Int, String>,
    ): AiChatAppViewState {
        val tabs = tabNumbers.map { tabNumber ->
            createTab(
                number = tabNumber,
                storageType = storageType,
                taskSnapshot = taskSnapshotsByTab[tabNumber],
                systemPrompt = systemPromptsByTab[tabNumber].orEmpty(),
            )
        }
        return AiChatAppViewState(
            tabs = tabs,
            activeTabNumber = activeTabNumber
                ?.takeIf { number -> tabs.any { it.number == number } }
                ?: tabs.first().number,
            selectedStorageType = storageType,
            storageDirectoryLabel = storageDirectoryLabel,
        )
    }

    private fun createNewTab(): ChatTab {
        val number = nextTabNumber
        nextTabNumber += 1
        return createTab(number, state.selectedStorageType)
    }

    private fun createTab(
        number: Int,
        storageType: ChatStorageType,
        taskSnapshot: TaskSessionSnapshot? = null,
        systemPrompt: String = "",
    ): ChatTab {
        val viewModel = createChatViewModel(number, storageType, systemPrompt)
        viewModel.loadModels()

        return ChatTab(
            number = number,
            title = createInitialTabTitle(number),
            viewModel = viewModel,
            taskSession = taskSnapshot?.toTaskModeSession(storageType),
        )
    }

    private fun TaskSessionSnapshot.toTaskModeSession(storageType: ChatStorageType): TaskModeSession =
        TaskModeSession(
            isModeEnabled = isModeEnabled,
            context = context,
            selectedStage = selectedStage,
            chatFocus = context.defaultTaskChatFocus(),
            stageAgents = stages.map { session ->
                val viewModel = createTaskStageChatViewModel(
                    session.chatId,
                    storageType,
                    session.systemPrompt,
                    session.startUserPrompt.takeIf { session.output == null }.orEmpty(),
                )
                viewModel.loadModels()
                TaskStageAgent(
                    session = session,
                    viewModel = viewModel,
                )
            },
            pendingTransition = pendingTransition,
            pendingRejection = pendingRejection,
        )

    private fun addTab() {
        val tab = createNewTab()
        state = state.copy(
            tabs = state.tabs + tab,
            activeTabNumber = tab.number,
        )
        notifyWorkspaceChanged()
    }

    private fun openProfileDialog() {
        val storageType = state.selectedStorageType
        state = state.copy(
            isProfileDialogOpen = true,
            profileDraft = EmptyProfileTemplate,
            profileError = null,
            isProfileInterviewActive = false,
            isProfileInterviewLoading = false,
            isProfileSaving = false,
        )
        coroutineScope.launch {
            runCatching { loadProfileAction(storageType) }
                .onSuccess { profileText ->
                    state = state.copy(
                        profileDraft = profileText.ifBlank { EmptyProfileTemplate },
                        profileError = null,
                    )
                }
                .onFailure { exception ->
                    if (exception is CancellationException) {
                        throw exception
                    }
                    state = state.copy(profileError = formatProfileError(exception))
                }
        }
    }

    private fun closeProfileDialog() {
        state = state.copy(
            isProfileDialogOpen = false,
            isProfileInterviewActive = false,
            isProfileInterviewLoading = false,
            isProfileSaving = false,
            profileError = null,
        )
    }

    private fun saveProfile() {
        if (!state.isProfileActionEnabled) {
            return
        }
        val storageType = state.selectedStorageType
        val text = state.profileDraft
        state = state.copy(isProfileSaving = true, profileError = null)
        coroutineScope.launch {
            runCatching { saveProfileAction(storageType, text) }
                .onSuccess { savedText ->
                    state = state.copy(
                        profileDraft = savedText.ifBlank { EmptyProfileTemplate },
                        isProfileSaving = false,
                        isProfileDialogOpen = false,
                    )
                }
                .onFailure { exception ->
                    if (exception is CancellationException) {
                        throw exception
                    }
                    state = state.copy(
                        isProfileSaving = false,
                        profileError = formatProfileError(exception),
                    )
                }
        }
    }

    private fun startProfileInterview() {
        if (!state.isProfileActionEnabled) {
            return
        }
        state = state.copy(
            isProfileInterviewActive = true,
            profileInterviewQuestionIndex = 0,
            profileInterviewAnswers = emptyList(),
            profileInterviewAnswerInput = "",
            profileError = null,
        )
    }

    private fun submitProfileInterviewAnswer() {
        if (!state.isProfileActionEnabled || !state.isProfileInterviewActive) {
            return
        }

        val answer = state.profileInterviewAnswerInput.trim()
        val answers = state.profileInterviewAnswers + answer
        val nextIndex = state.profileInterviewQuestionIndex + 1
        if (nextIndex < ProfileInterviewQuestionsCount) {
            state = state.copy(
                profileInterviewAnswers = answers,
                profileInterviewQuestionIndex = nextIndex,
                profileInterviewAnswerInput = "",
                profileError = null,
            )
            return
        }

        val activeViewModel = state.activeTab?.viewModel
        val providerName = activeViewModel?.selectedModelProviderName.orEmpty()
        val modelId = activeViewModel?.selectedModelId.orEmpty()
        val currentProfile = state.profileDraft
        state = state.copy(
            isProfileInterviewLoading = true,
            profileInterviewAnswers = answers,
            profileInterviewAnswerInput = "",
            profileError = null,
        )
        coroutineScope.launch {
            runCatching {
                updateProfileFromInterviewAction(
                    providerName,
                    modelId,
                    currentProfile,
                    answers,
                )
            }.onSuccess { updatedProfile ->
                state = state.copy(
                    profileDraft = updatedProfile.ifBlank { EmptyProfileTemplate },
                    isProfileInterviewActive = false,
                    isProfileInterviewLoading = false,
                )
            }.onFailure { exception ->
                if (exception is CancellationException) {
                    throw exception
                }
                state = state.copy(
                    isProfileInterviewActive = false,
                    isProfileInterviewLoading = false,
                    profileError = formatProfileError(exception),
                )
            }
        }
    }

    private fun openInvariantsDialog() {
        val storageType = state.selectedStorageType
        state = state.copy(
            isInvariantsDialogOpen = true,
            invariantsDraft = EmptyInvariantsTemplate,
            invariantsError = null,
            isInvariantsSaving = false,
            isInvariantsApplying = false,
            invariantsChatMessages = state.invariantsChatMessages.ifEmpty { initialInvariantsChatMessages() },
            invariantsChatInput = "",
        )
        coroutineScope.launch {
            runCatching { loadInvariantsAction(storageType) }
                .onSuccess { invariants ->
                    state = state.copy(
                        invariantsDraft = invariants.toDraftText().ifBlank { EmptyInvariantsTemplate },
                        invariantsError = null,
                    )
                }
                .onFailure { exception ->
                    if (exception is CancellationException) {
                        throw exception
                    }
                    state = state.copy(invariantsError = formatProfileError(exception))
                }
        }
    }

    private fun closeInvariantsDialog() {
        state = state.copy(
            isInvariantsDialogOpen = false,
            isInvariantsSaving = false,
            isInvariantsApplying = false,
            invariantsChatMessages = emptyList(),
            invariantsChatInput = "",
            invariantsError = null,
        )
    }

    private fun saveInvariants() {
        if (!state.isInvariantsActionEnabled) {
            return
        }
        val storageType = state.selectedStorageType
        val invariants = state.invariantsDraft.toInvariants()
        state = state.copy(isInvariantsSaving = true, invariantsError = null)
        coroutineScope.launch {
            runCatching { saveInvariantsAction(storageType, invariants) }
                .onSuccess { savedInvariants ->
                    state = state.copy(
                        invariantsDraft = savedInvariants.toDraftText().ifBlank { EmptyInvariantsTemplate },
                        isInvariantsSaving = false,
                        isInvariantsDialogOpen = false,
                    )
                }
                .onFailure { exception ->
                    if (exception is CancellationException) {
                        throw exception
                    }
                    state = state.copy(
                        isInvariantsSaving = false,
                        invariantsError = formatProfileError(exception),
                    )
                }
        }
    }

    private fun sendInvariantsChatMessage() {
        if (!state.isInvariantsActionEnabled) {
            return
        }
        val text = state.invariantsChatInput.trim()
        if (text.isEmpty()) {
            return
        }

        state = state.copy(
            invariantsChatMessages = state.invariantsChatMessages.ifEmpty { initialInvariantsChatMessages() } +
                InvariantsChatMessage(role = InvariantsChatRole.User, text = text),
            invariantsChatInput = "",
            invariantsError = null,
        )
    }

    private fun applyInvariantsChat() {
        if (!state.isInvariantsActionEnabled) {
            return
        }
        val activeViewModel = state.activeTab?.viewModel
        val providerName = activeViewModel?.selectedModelProviderName.orEmpty()
        val modelId = activeViewModel?.selectedModelId.orEmpty()
        val currentInvariants = state.invariantsDraft.toInvariants()
        val chatMessages = state.invariantsChatMessages.ifEmpty { initialInvariantsChatMessages() }
        state = state.copy(
            isInvariantsApplying = true,
            invariantsError = null,
        )
        coroutineScope.launch {
            runCatching {
                updateInvariantsFromChatAction(
                    providerName,
                    modelId,
                    currentInvariants,
                    chatMessages.toCollectionMessages(),
                )
            }.onSuccess { updatedInvariants ->
                state = state.copy(
                    invariantsDraft = updatedInvariants.toDraftText().ifBlank { EmptyInvariantsTemplate },
                    isInvariantsApplying = false,
                )
            }.onFailure { exception ->
                if (exception is CancellationException) {
                    throw exception
                }
                state = state.copy(
                    isInvariantsApplying = false,
                    invariantsError = formatProfileError(exception),
                )
            }
        }
    }

    private fun closeTab(number: Int) {
        val currentTabs = state.tabs
        val closingIndex = currentTabs.indexOfFirst { it.number == number }
        if (closingIndex == -1) {
            return
        }

        val remainingTabs = currentTabs.filterNot { it.number == number }
        if (remainingTabs.isEmpty()) {
            onTabClosed(number, state.selectedStorageType)
            val replacementTab = createNewTab()
            state = state.copy(
                tabs = listOf(replacementTab),
                activeTabNumber = replacementTab.number,
            )
            notifyWorkspaceChanged()
            return
        }

        val activeTabNumber = if (state.activeTabNumber == number) {
            val rightNeighbor = currentTabs.drop(closingIndex + 1).firstOrNull()
            val leftNeighbor = currentTabs.take(closingIndex).lastOrNull()
            rightNeighbor?.number ?: leftNeighbor?.number ?: remainingTabs.first().number
        } else {
            state.activeTabNumber
        }

        state = state.copy(
            tabs = remainingTabs,
            activeTabNumber = activeTabNumber,
        )
        onTabClosed(number, state.selectedStorageType)
        notifyWorkspaceChanged()
    }

    private fun selectStorage(storageType: ChatStorageType) {
        if (storageType == state.selectedStorageType) {
            state = state.copy(isStorageMenuExpanded = false)
            return
        }
        if (!state.isStorageSwitchEnabled) {
            state = state.copy(isStorageMenuExpanded = false)
            return
        }

        val result = switchStorage(
            state.selectedStorageType,
            storageType,
            state.tabs,
            state.activeTabNumber,
            nextTabNumber,
        )
        nextTabNumber = result.nextTabNumber
        state = state.copy(
            tabs = result.tabs,
            activeTabNumber = result.activeTabNumber,
            selectedStorageType = storageType,
            isStorageMenuExpanded = false,
        )
        notifyWorkspaceChanged()
    }

    private fun handleChatEvent(event: ChatEvent) {
        val activeViewModel = state.activeTab?.viewModel ?: return
        when (event) {
            ChatEvent.SendClicked -> {
                val activeTab = state.activeTab ?: return
                val taskSession = activeTab.taskSession
                if (taskSession?.isModeEnabled == true) {
                    handleTaskModeOrchestratorPrompt(activeTab)
                } else {
                    updateActiveTabTitleIfNeeded(activeViewModel.state.prompt)
                    activeViewModel.sendPrompt()
                }
            }
            else -> {
                val activeTab = state.activeTab
                if (activeTab?.taskSession?.isModeEnabled == true && event.isPromptInputEvent()) {
                    focusTaskChat(activeTab.number, TaskChatFocus.Orchestrator)
                }
                activeViewModel.onEvent(event)
                if (event is ChatEvent.SystemPromptChanged) {
                    notifyWorkspaceChanged()
                }
            }
        }
    }

    private fun toggleTaskMode() {
        val activeTab = state.activeTab ?: return
        val currentSession = activeTab.taskSession
        val nextSession = if (currentSession == null) {
            TaskModeSession(isModeEnabled = true)
        } else {
            currentSession.copy(isModeEnabled = !currentSession.isModeEnabled)
        }
        updateTab(activeTab.number) { tab -> tab.copy(taskSession = nextSession) }
    }

    private fun selectTaskStage(stage: TaskState) {
        val activeTab = state.activeTab ?: return
        val taskSession = activeTab.taskSession ?: return
        val context = taskSession.context
        val isReached = taskSession.stageAgents.any { it.session.state == stage } ||
            (context != null && stage.ordinal <= context.state.ordinal)
        if (!isReached) {
            return
        }

        val nextFocus = if (context != null && stage == context.state) {
            TaskChatFocus.Stage(stage)
        } else {
            taskSession.chatFocus
        }
        updateTab(activeTab.number) { tab ->
            tab.copy(taskSession = taskSession.copy(selectedStage = stage, chatFocus = nextFocus))
        }
    }

    private fun handleTaskModeOrchestratorPrompt(activeTab: ChatTab) {
        val prompt = activeTab.viewModel.state.prompt.trim()
        if (prompt.isBlank()) {
            return
        }
        updateActiveTabTitleIfNeeded(prompt)

        val taskSession = activeTab.taskSession
        if (taskSession?.context == null) {
            focusTaskChat(activeTab.number, TaskChatFocus.Orchestrator)
            activeTab.viewModel.setPrompt("")
            activeTab.viewModel.appendPersistentMessage(
                ChatMessage(role = ChatRole.User, content = prompt),
            ) {
                startTask(activeTab.number, prompt)
            }
        } else if (
            taskSession.pendingRejection != null &&
            taskSession.context.expectedAction == TaskExpectedAction.UserPrompt
        ) {
            focusTaskChat(activeTab.number, TaskChatFocus.Orchestrator)
            activeTab.viewModel.setPrompt("")
            activeTab.viewModel.appendPersistentMessage(
                ChatMessage(role = ChatRole.User, content = prompt),
            ) {
                sendRejectionClarificationToOrchestrator(activeTab.number, prompt)
            }
        } else {
            if (taskSession.isOrchestratorFsmFlowRunning) {
                return
            }
            focusTaskChat(activeTab.number, TaskChatFocus.Orchestrator)
            setOrchestratorFsmFlowRunning(activeTab.number, true)
            activeTab.viewModel.sendPrompt(runtimeSystemPrompt = buildTaskOrchestratorRuntimePrompt(taskSession)) {
                handleTaskOrchestratorCommand(
                    tabNumber = activeTab.number,
                    orchestratorOutput = it.lastAssistantOutput(),
                )
            }
        }
    }

    private fun handleTaskOrchestratorCommand(
        tabNumber: Int,
        orchestratorOutput: String,
    ) {
        val command = parseTaskOrchestratorCommand(orchestratorOutput)
        when (command) {
            TaskOrchestratorCommand.DiscussOnly,
            is TaskOrchestratorCommand.AskUser -> {
                setOrchestratorFsmFlowRunning(tabNumber, false)
            }

            is TaskOrchestratorCommand.DelegateCurrentStage -> {
                handleTaskStageDelegationCommand(tabNumber, command)
            }

            is TaskOrchestratorCommand.AcceptCurrentStage -> {
                handleTaskButtonOnlyCommand(tabNumber, command)
            }

            is TaskOrchestratorCommand.RejectCurrentStage -> {
                handleTaskButtonOnlyCommand(tabNumber, command)
            }

            is TaskOrchestratorCommand.RequestStageTransition -> {
                handleTaskStageTransitionRequest(tabNumber, command)
            }
        }
    }

    private fun handleTaskStageDelegationCommand(
        tabNumber: Int,
        command: TaskOrchestratorCommand.DelegateCurrentStage,
    ) {
        val activeTab = state.tabs.firstOrNull { it.number == tabNumber }
        val taskSession = activeTab?.taskSession
        val context = taskSession?.context
        if (activeTab == null || taskSession == null || context == null) {
            setOrchestratorFsmFlowRunning(tabNumber, false)
            return
        }
        val runtimeState = taskSession.toMachineRuntimeState()
        val availability = taskMachine.allowedActions(context, runtimeState)
        val blockReason = taskSession.blockedDelegationReason(
            context = context,
            availability = availability,
            command = command,
        )
        if (blockReason != null) {
            appendTaskStateEvent(
                tabNumber = tabNumber,
                content = buildTaskOrchestratorCommandBlockedEvent(
                    command = command,
                    currentStage = context.state,
                    reason = blockReason,
                ),
            ) {
                sendTaskMachineFollowUpPrompt(
                    tabNumber = tabNumber,
                    command = command,
                    result = "Task State Machine blocked the command. Reason: $blockReason",
                )
            }
            return
        }

        val stage = command.targetStage
        val input = command.inputForStage
        val nextSession = taskSession.ensureStageAgent(
            tabNumber = tabNumber,
            storageType = state.selectedStorageType,
            stage = stage,
            input = input,
        ).copy(
            context = context,
            selectedStage = stage,
            chatFocus = TaskChatFocus.Stage(stage),
            pendingTransition = null,
            pendingRejection = null,
        )
        updateTab(tabNumber) { tab -> tab.copy(taskSession = nextSession) }
        appendTaskStateEvent(
            tabNumber = tabNumber,
            content = buildTaskOrchestratorDelegatedStageEvent(
                stage = stage,
                reason = command.reason,
            ),
        ) {
            sendStagePrompt(tabNumber, stage, input) {
                setOrchestratorFsmFlowRunning(tabNumber, false)
            }
        }
    }

    private fun handleTaskButtonOnlyCommand(
        tabNumber: Int,
        command: TaskOrchestratorCommand,
    ) {
        val taskSession = state.tabs.firstOrNull { it.number == tabNumber }?.taskSession
        if (taskSession == null) {
            setOrchestratorFsmFlowRunning(tabNumber, false)
            return
        }
        val reason = "Stage results can be accepted or rejected only through the explicit UI buttons."
        appendTaskStateEvent(
            tabNumber = tabNumber,
            content = buildTaskOrchestratorCommandBlockedEvent(
                command = command,
                currentStage = taskSession.context?.state,
                reason = reason,
            ),
        ) {
            sendTaskMachineFollowUpPrompt(
                tabNumber = tabNumber,
                command = command,
                result = "Task State Machine blocked the command. Reason: $reason",
            )
        }
    }

    private fun handleTaskStageTransitionRequest(
        tabNumber: Int,
        command: TaskOrchestratorCommand.RequestStageTransition,
    ) {
        val taskSession = state.tabs.firstOrNull { it.number == tabNumber }?.taskSession
        if (taskSession == null) {
            setOrchestratorFsmFlowRunning(tabNumber, false)
            return
        }
        val reason = taskSession.blockedTransitionRequestReason()
        appendTaskStateEvent(
            tabNumber = tabNumber,
            content = buildTaskOrchestratorCommandBlockedEvent(
                command = command,
                currentStage = taskSession.context?.state,
                reason = reason,
            ),
        ) {
            sendTaskMachineFollowUpPrompt(
                tabNumber = tabNumber,
                command = command,
                result = "Task State Machine blocked the direct transition request. Reason: $reason",
            )
        }
    }

    private fun TaskModeSession.blockedDelegationReason(
        context: TaskContext,
        availability: TaskActionAvailability,
        command: TaskOrchestratorCommand.DelegateCurrentStage,
    ): String? =
        when {
            command.targetStage != context.state ->
                "Cannot delegate ${command.targetStage.title}; current FSM stage is ${context.state.title}."
            !availability.isAllowed(TaskAllowedAction.DelegateCurrentStage) ->
                availability.reasonFor(TaskAllowedAction.DelegateCurrentStage)
                    ?: "Current FSM state does not allow stage delegation."
            stageAgents.firstOrNull { it.session.state == context.state }?.viewModel?.state?.isLoading == true ->
                "Current ${context.state.title} stage agent is already working."
            command.inputForStage.isBlank() ->
                "Delegation command does not contain input_for_stage."
            else -> null
        }

    private fun startTask(tabNumber: Int, task: String) {
        val activeTab = state.tabs.firstOrNull { it.number == tabNumber } ?: return
        val context = taskMachine.start(task)
        val input = buildStageInput(
            context = context,
            stage = TaskState.Planning,
            previousOutput = null,
            additionalInput = null,
        )
        val agent = createStageAgent(
            tabNumber = activeTab.number,
            storageType = state.selectedStorageType,
            stage = TaskState.Planning,
            input = input,
        )
        val session = TaskModeSession(
            isModeEnabled = true,
            context = context,
            selectedStage = TaskState.Planning,
            chatFocus = TaskChatFocus.Stage(TaskState.Planning),
            stageAgents = listOf(agent),
            pendingTransition = null,
        )

        updateTab(activeTab.number) { tab -> tab.copy(taskSession = session) }
        appendTaskStateEvent(
            tabNumber = activeTab.number,
            content = buildTaskStartedEvent(context),
        ) {
            sendStagePrompt(activeTab.number, TaskState.Planning, input)
        }
    }

    private fun acceptTaskTransition(
        tabNumber: Int,
        onCompleted: (() -> Unit)? = null,
    ): Boolean {
        val activeTab = state.tabs.firstOrNull { it.number == tabNumber } ?: return false
        val taskSession = activeTab.taskSession ?: return false
        val context = taskSession.context ?: return false
        val stageResult = taskSession.currentFinalStageResult(context) ?: return false
        if (stageResult.status == TaskStageResultStatus.Blocked) {
            acceptBlockedTaskStage(
                tabNumber = tabNumber,
                taskSession = taskSession,
                context = context,
                stageResult = stageResult,
                onCompleted = onCompleted,
            )
            return true
        }
        val output = stageResult.output.takeIf { it.isNotBlank() } ?: return false
        val completedContext = taskMachine.completeStage(
            context = context,
            output = output,
            plan = if (context.state == TaskState.Planning) extractPlanItems(output) else context.plan,
        )
        val nextStage = context.state.next()
        if (nextStage == null) {
            updateTab(tabNumber) { tab ->
                tab.copy(
                    taskSession = taskSession.copy(
                        context = completedContext,
                        pendingTransition = null,
                        pendingRejection = null,
                        selectedStage = context.state,
                        chatFocus = TaskChatFocus.Stage(context.state),
                    ),
                )
            }
            appendTaskStateEvent(
                tabNumber = tabNumber,
                content = buildStageResultAcceptedEvent(context.state, output, pendingTransition = null),
            ) {
                onCompleted?.invoke()
            }
            return true
        }
        val proposal = taskMachine.proposeTransition(
            context = completedContext,
            to = nextStage,
            reason = "${context.state.title} accepted by user",
            inputForTarget = buildStageInput(
                context = completedContext.copy(state = nextStage, current = nextStage.title),
                stage = nextStage,
                previousOutput = output,
                additionalInput = null,
            ),
        )
        val acceptedContext = taskMachine.acceptTransition(completedContext, proposal)
        val nextSession = taskSession.ensureStageAgent(
            tabNumber = tabNumber,
            storageType = state.selectedStorageType,
            stage = proposal.to,
            input = proposal.inputForTarget,
        ).copy(
            context = acceptedContext,
            selectedStage = proposal.to,
            chatFocus = TaskChatFocus.Stage(proposal.to),
            pendingTransition = null,
            pendingRejection = null,
        )

        updateTab(tabNumber) { tab -> tab.copy(taskSession = nextSession) }
        appendTaskStateEvent(
            tabNumber = tabNumber,
            content = buildStageResultAcceptedEvent(context.state, output, pendingTransition = proposal),
        ) {
            sendStagePrompt(tabNumber, proposal.to, proposal.inputForTarget, onCompleted = onCompleted)
        }
        return true
    }

    private fun rejectTaskStage(
        tabNumber: Int,
        userClarification: String? = null,
        onCompleted: (() -> Unit)? = null,
    ): Boolean {
        val activeTab = state.tabs.firstOrNull { it.number == tabNumber } ?: return false
        val taskSession = activeTab.taskSession ?: return false
        val context = taskSession.context ?: return false
        if (activeTab.viewModel.state.isLoading) {
            return false
        }
        val stageResult = taskSession.currentFinalStageResult(context) ?: return false
        val rejectedOutput = stageResult.output.takeIf { it.isNotBlank() } ?: return false
        val completedContext = if (stageResult.status == TaskStageResultStatus.Completed) {
            taskMachine.completeStage(
                context = context,
                output = rejectedOutput,
                plan = if (context.state == TaskState.Planning) extractPlanItems(rejectedOutput) else context.plan,
            )
        } else {
            context.copy(expectedAction = TaskExpectedAction.UserConfirmation)
        }
        val rejectedContext = if (completedContext.expectedAction == TaskExpectedAction.UserConfirmation) {
            taskMachine.rejectStage(completedContext)
        } else {
            completedContext.copy(expectedAction = TaskExpectedAction.OrchestratorDecision)
        }
        val proposedNextStage = if (stageResult.status == TaskStageResultStatus.Completed) context.state.next() else null
        val proposedInputForTarget = proposedNextStage?.let { target ->
            buildStageInput(
                context = completedContext.copy(state = target, current = target.title),
                stage = target,
                previousOutput = rejectedOutput,
                additionalInput = null,
            )
        }
        val rejection = TaskStageRejection(
            stage = context.state,
            rejectedOutput = rejectedOutput,
            context = completedContext,
            proposedNextStage = proposedNextStage,
            proposedInputForTarget = proposedInputForTarget,
            reason = stageResult.reason.takeIf { it.isNotBlank() },
        )

        updateTab(activeTab.number) { tab ->
            tab.copy(
                taskSession = taskSession.copy(
                    context = rejectedContext,
                    selectedStage = context.state,
                    chatFocus = TaskChatFocus.Orchestrator,
                    pendingTransition = null,
                    pendingRejection = rejection,
                ),
            )
        }
        appendTaskStateEvent(
            tabNumber = tabNumber,
            content = buildStageRejectedEvent(rejection),
        ) {
            sendRejectionAnalysisPrompt(
                tabNumber = tabNumber,
                rejection = rejection,
                userClarification = userClarification,
                onCompleted = onCompleted,
            )
        }
        return true
    }

    private fun acceptBlockedTaskStage(
        tabNumber: Int,
        taskSession: TaskModeSession,
        context: TaskContext,
        stageResult: TaskStageResult,
        onCompleted: (() -> Unit)?,
    ) {
        val analysisContext = context.copy(expectedAction = TaskExpectedAction.OrchestratorDecision)
        val rejection = TaskStageRejection(
            stage = context.state,
            rejectedOutput = stageResult.output,
            context = context,
            reason = stageResult.reason.takeIf { it.isNotBlank() }
                ?: "User accepted that the stage is blocked.",
        )
        updateTab(tabNumber) { tab ->
            tab.copy(
                taskSession = taskSession.copy(
                    context = analysisContext,
                    selectedStage = context.state,
                    chatFocus = TaskChatFocus.Orchestrator,
                    pendingTransition = null,
                    pendingRejection = rejection,
                ),
            )
        }
        appendTaskStateEvent(
            tabNumber = tabNumber,
            content = buildBlockedStageAcceptedEvent(context.state, stageResult),
        ) {
            sendRejectionAnalysisPrompt(
                tabNumber = tabNumber,
                rejection = rejection,
                userClarification = "The user accepted the blocked result. Analyze how the FSM should proceed without treating this as a successful stage completion.",
                onCompleted = onCompleted,
            )
        }
    }

    private fun sendRejectionClarificationToOrchestrator(
        tabNumber: Int,
        clarification: String,
    ) {
        val activeTab = state.tabs.firstOrNull { it.number == tabNumber } ?: return
        val taskSession = activeTab.taskSession ?: return
        val rejection = taskSession.pendingRejection ?: return
        val context = taskSession.context ?: return
        val analysisContext = taskMachine.resumeRejectionAnalysis(context)

        updateTab(activeTab.number) { tab ->
            tab.copy(
                taskSession = taskSession.copy(
                    context = analysisContext,
                    chatFocus = TaskChatFocus.Orchestrator,
                    pendingRejection = rejection.copy(question = null),
                    pendingTransition = null,
                ),
            )
        }
        appendTaskStateEvent(
            tabNumber = activeTab.number,
            content = buildRejectionClarificationEvent(rejection),
        ) {
            sendRejectionAnalysisPrompt(
                tabNumber = activeTab.number,
                rejection = rejection,
                userClarification = clarification,
                onCompleted = null,
            )
        }
    }

    private fun sendRejectionAnalysisPrompt(
        tabNumber: Int,
        rejection: TaskStageRejection,
        userClarification: String?,
        onCompleted: (() -> Unit)?,
    ) {
        val activeTab = state.tabs.firstOrNull { it.number == tabNumber } ?: return
        val taskSession = activeTab.taskSession ?: return
        val context = taskSession.context ?: return
        if (activeTab.viewModel.state.isLoading) {
            return
        }
        val analysisContext = if (context.expectedAction == TaskExpectedAction.OrchestratorDecision) {
            context
        } else {
            taskMachine.resumeRejectionAnalysis(context)
        }
        updateTab(tabNumber) { tab ->
            val session = tab.taskSession ?: return@updateTab tab
            tab.copy(
                taskSession = session.copy(
                    context = analysisContext,
                    chatFocus = TaskChatFocus.Orchestrator,
                    pendingTransition = null,
                    pendingRejection = rejection.copy(question = null),
                ),
            )
        }

        val updatedTaskSession = state.tabs.firstOrNull { it.number == tabNumber }?.taskSession ?: taskSession
        activeTab.viewModel.sendSyntheticPrompt(
            prompt = buildRejectionAnalysisPrompt(rejection, userClarification),
            runtimeSystemPrompt = buildTaskOrchestratorRuntimePrompt(updatedTaskSession),
        ) { orchestratorState ->
            handleRejectionDecision(
                tabNumber = tabNumber,
                orchestratorOutput = orchestratorState.lastAssistantOutput(),
                onCompleted = onCompleted,
            )
        }
    }

    private fun handleRejectionDecision(
        tabNumber: Int,
        orchestratorOutput: String,
        onCompleted: (() -> Unit)? = null,
    ) {
        val tab = state.tabs.firstOrNull { it.number == tabNumber } ?: return
        val taskSession = tab.taskSession ?: return
        val rejection = taskSession.pendingRejection ?: return
        val context = taskSession.context ?: return
        val decision = parseTaskOrchestratorDecision(orchestratorOutput)
            ?: TaskOrchestratorDecision.AskUser(
                question = defaultRejectionQuestion(rejection.stage),
                reason = "Orchestrator decision was not structured enough to parse.",
            )

        when (decision) {
            is TaskOrchestratorDecision.RetryCurrent -> {
                runRejectedStageDecision(
                    tabNumber = tabNumber,
                    rejection = rejection,
                    target = context.state,
                    reason = decision.reason,
                    additionalInput = decision.additionalInput,
                    onCompleted = onCompleted,
                )
            }

            is TaskOrchestratorDecision.ReturnPrevious -> {
                val previous = context.state.previous()
                if (previous == null) {
                    askUserForRejectionDetails(
                        tabNumber = tabNumber,
                        rejection = rejection,
                        question = defaultRejectionQuestion(rejection.stage),
                        reason = "Cannot return before ${context.state.title}.",
                        onCompleted = onCompleted,
                    )
                } else {
                    runRejectedStageDecision(
                        tabNumber = tabNumber,
                        rejection = rejection,
                        target = previous,
                        reason = decision.reason,
                        additionalInput = decision.additionalInput,
                        onCompleted = onCompleted,
                    )
                }
            }

            is TaskOrchestratorDecision.AskUser -> {
                askUserForRejectionDetails(
                    tabNumber = tabNumber,
                    rejection = rejection,
                    question = decision.question,
                    reason = decision.reason,
                    onCompleted = onCompleted,
                )
            }
        }
    }

    private fun runRejectedStageDecision(
        tabNumber: Int,
        rejection: TaskStageRejection,
        target: TaskState,
        reason: String,
        additionalInput: String,
        onCompleted: (() -> Unit)? = null,
    ) {
        val tab = state.tabs.firstOrNull { it.number == tabNumber } ?: return
        val taskSession = tab.taskSession ?: return
        val context = taskSession.context ?: return
        val nextContext = taskMachine.resolveRejectedStage(context, target)
        val previousOutput = if (target == rejection.stage) {
            rejection.rejectedOutput
        } else {
            taskSession.stageAgents.firstOrNull { it.session.state == target }?.session?.output
        }
        val input = buildStageInput(
            context = nextContext,
            stage = target,
            previousOutput = previousOutput,
            additionalInput = buildRejectedStageAdditionalInput(
                rejection = rejection,
                reason = reason,
                additionalInput = additionalInput,
            ),
        )
        val nextSession = taskSession.ensureStageAgent(
            tabNumber = tabNumber,
            storageType = state.selectedStorageType,
            stage = target,
            input = input,
        ).copy(
            context = nextContext,
            selectedStage = target,
            chatFocus = TaskChatFocus.Stage(target),
            pendingTransition = null,
            pendingRejection = null,
        )

        updateTab(tabNumber) { currentTab -> currentTab.copy(taskSession = nextSession) }
        appendTaskStateEvent(
            tabNumber = tabNumber,
            content = buildRejectedStageDecisionEvent(
                rejection = rejection,
                target = target,
                reason = reason,
            ),
        ) {
            sendStagePrompt(tabNumber, target, input, onCompleted = onCompleted)
        }
    }

    private fun askUserForRejectionDetails(
        tabNumber: Int,
        rejection: TaskStageRejection,
        question: String,
        reason: String,
        onCompleted: (() -> Unit)? = null,
    ) {
        val tab = state.tabs.firstOrNull { it.number == tabNumber } ?: return
        val taskSession = tab.taskSession ?: return
        val context = taskSession.context ?: return
        val waitingContext = taskMachine.awaitRejectionDetails(context)

        updateTab(tabNumber) { currentTab ->
            currentTab.copy(
                taskSession = taskSession.copy(
                    context = waitingContext,
                    chatFocus = TaskChatFocus.Orchestrator,
                    pendingTransition = null,
                    pendingRejection = rejection.copy(
                        question = question,
                        reason = reason,
                    ),
                ),
            )
        }
        appendTaskStateEvent(
            tabNumber = tabNumber,
            content = buildRejectionQuestionEvent(
                rejection = rejection,
                reason = reason,
            ),
        ) {
            tab.viewModel.appendPersistentMessage(
                ChatMessage(
                    role = ChatRole.Assistant,
                    content = question,
                ),
            ) {
                onCompleted?.invoke()
            }
        }
    }

    private fun handleTaskStageChatEvent(event: ChatEvent) {
        val activeTab = state.activeTab ?: return
        val taskSession = activeTab.taskSession ?: return
        val agent = taskSession.selectedStageAgent ?: return
        val currentStage = taskSession.context?.state ?: return
        if (agent.session.state != currentStage && event.isPromptInputEvent()) {
            return
        }
        if (event.isPromptInputEvent()) {
            focusTaskChat(activeTab.number, TaskChatFocus.Stage(agent.session.state))
        }
        when (event) {
            ChatEvent.SendClicked -> {
                agent.viewModel.syncRequestSettingsFrom(activeTab.viewModel.state)
                agent.viewModel.sendPrompt { stageState ->
                    handleTaskStageResponse(
                        tabNumber = activeTab.number,
                        stage = agent.session.state,
                        assistantOutput = stageState.lastAssistantOutput(),
                    )
                }
            }
            else -> agent.viewModel.onEvent(event)
        }
    }

    private fun createStageAgent(
        tabNumber: Int,
        storageType: ChatStorageType,
        stage: TaskState,
        input: String,
    ): TaskStageAgent {
        val chatId = taskStageChatId(tabNumber, stage)
        val systemPrompt = buildStageSystemPrompt(stage)
        val viewModel = createTaskStageChatViewModel(
            chatId,
            storageType,
            systemPrompt,
            input,
        )
        viewModel.loadModels()
        return TaskStageAgent(
            session = TaskStageSession(
                state = stage,
                chatId = chatId,
                systemPrompt = systemPrompt,
                startUserPrompt = input,
                input = input,
                isReached = true,
            ),
            viewModel = viewModel,
        )
    }

    private fun TaskModeSession.ensureStageAgent(
        tabNumber: Int,
        storageType: ChatStorageType,
        stage: TaskState,
        input: String,
    ): TaskModeSession {
        val existing = stageAgents.firstOrNull { it.session.state == stage }
        if (existing == null) {
            return copy(stageAgents = stageAgents + createStageAgent(tabNumber, storageType, stage, input))
        }
        return copy(
            stageAgents = stageAgents.map { agent ->
                if (agent.session.state == stage) {
                    agent.copy(
                        session = agent.session.copy(
                            startUserPrompt = input,
                            input = input,
                            output = null,
                            resultStatus = TaskStageResultStatus.InProgress,
                            resultQuestion = "",
                            resultReason = "",
                            isReached = true,
                        ),
                    )
                } else {
                    agent
                }
            },
        )
    }

    private fun sendStagePrompt(
        tabNumber: Int,
        stage: TaskState,
        input: String,
        onCompleted: (() -> Unit)? = null,
    ) {
        val activeTab = state.tabs.firstOrNull { it.number == tabNumber } ?: return
        val taskSession = activeTab.taskSession ?: return
        val agent = taskSession.stageAgents.firstOrNull { it.session.state == stage } ?: return
        agent.viewModel.syncRequestSettingsFrom(activeTab.viewModel.state)
        agent.viewModel.setPrompt(input)
        updateTab(tabNumber) { tab ->
            val session = tab.taskSession ?: return@updateTab tab
            tab.copy(
                taskSession = session.copy(
                    context = session.context?.copy(expectedAction = TaskExpectedAction.AgentWork),
                    selectedStage = stage,
                    chatFocus = TaskChatFocus.Stage(stage),
                    pendingTransition = null,
                    pendingRejection = null,
                ),
            )
        }
        agent.viewModel.sendPrompt { stageState ->
            handleTaskStageResponse(
                tabNumber = tabNumber,
                stage = stage,
                assistantOutput = stageState.lastAssistantOutput(),
                onCompleted = onCompleted,
            )
        }
    }

    private fun handleTaskStageResponse(
        tabNumber: Int,
        stage: TaskState,
        assistantOutput: String,
        onCompleted: (() -> Unit)? = null,
    ) {
        val tab = state.tabs.firstOrNull { it.number == tabNumber } ?: return
        val taskSession = tab.taskSession ?: return
        val context = taskSession.context ?: return
        val result = parseTaskStageResult(assistantOutput)
        val finalOutput = result.finalOutput()
        val updatedAgents = taskSession.stageAgents.map { agent ->
            if (agent.session.state == stage) {
                agent.copy(
                    session = agent.session.copy(
                        output = finalOutput,
                        resultStatus = result.status,
                        resultQuestion = result.question,
                        resultReason = result.reason,
                    ),
                )
            } else {
                agent
            }
        }
        updateTab(tabNumber) { currentTab ->
            val currentSession = currentTab.taskSession ?: return@updateTab currentTab
            currentTab.copy(
                taskSession = currentSession.copy(
                    context = context,
                    stageAgents = updatedAgents,
                    pendingTransition = null,
                    pendingRejection = null,
                    selectedStage = stage,
                    chatFocus = if (stage == context.state) {
                        TaskChatFocus.Stage(stage)
                    } else {
                        currentSession.chatFocus
                    },
                ),
            )
        }
        onCompleted?.invoke()
    }

    private fun appendTaskStateEvent(
        tabNumber: Int,
        content: String,
        onCompleted: () -> Unit = {},
    ) {
        val tab = state.tabs.firstOrNull { it.number == tabNumber } ?: return
        tab.viewModel.appendPersistentMessage(
            ChatMessage(
                role = ChatRole.Assistant,
                content = content,
                kind = ChatMessageKind.TaskStateEvent,
            ),
        ) {
            onCompleted()
        }
    }

    private fun sendTaskMachineFollowUpPrompt(
        tabNumber: Int,
        command: TaskOrchestratorCommand,
        result: String,
    ) {
        val tab = state.tabs.firstOrNull { it.number == tabNumber }
        val taskSession = tab?.taskSession
        if (tab == null || taskSession == null) {
            setOrchestratorFsmFlowRunning(tabNumber, false)
            return
        }
        tab.viewModel.sendSyntheticPrompt(
            prompt = buildTaskMachineFollowUpPrompt(
                command = command,
                result = result,
                taskSession = taskSession,
            ),
            runtimeSystemPrompt = buildTaskOrchestratorRuntimePrompt(
                taskSession = taskSession,
                allowCommands = false,
            ),
        ) {
            setOrchestratorFsmFlowRunning(tabNumber, false)
        }
    }

    private fun focusTaskChat(
        tabNumber: Int,
        focus: TaskChatFocus,
    ) {
        val taskSession = state.tabs.firstOrNull { it.number == tabNumber }?.taskSession ?: return
        if (taskSession.chatFocus == focus) {
            return
        }
        updateTab(tabNumber) { tab ->
            tab.copy(taskSession = taskSession.copy(chatFocus = focus))
        }
    }

    private fun setOrchestratorFsmFlowRunning(
        tabNumber: Int,
        isRunning: Boolean,
    ) {
        val taskSession = state.tabs.firstOrNull { it.number == tabNumber }?.taskSession ?: return
        if (taskSession.isOrchestratorFsmFlowRunning == isRunning) {
            return
        }
        updateTab(tabNumber) { tab ->
            val session = tab.taskSession ?: return@updateTab tab
            tab.copy(taskSession = session.copy(isOrchestratorFsmFlowRunning = isRunning))
        }
    }

    private fun updateTab(
        tabNumber: Int,
        transform: (ChatTab) -> ChatTab,
    ) {
        state = state.copy(
            tabs = state.tabs.map { tab ->
                if (tab.number == tabNumber) transform(tab) else tab
            },
        )
        notifyWorkspaceChanged()
    }

    private fun updateActiveTabTitleIfNeeded(prompt: String) {
        val activeTab = state.activeTab ?: return
        if (activeTab.title != ChatTab.NewTitle) {
            return
        }

        val title = prompt.toTabTitle()
        if (title == ChatTab.NewTitle) {
            return
        }

        state = state.copy(
            tabs = state.tabs.map { tab ->
                if (tab.number == activeTab.number) {
                    tab.copy(title = title)
                } else {
                    tab
                }
            },
        )
    }

    private fun notifyWorkspaceChanged() {
        onWorkspaceChanged(
            state.tabs.map { tab ->
                ChatTabSnapshot(
                    number = tab.number,
                    systemPrompt = tab.viewModel.state.systemPrompt,
                    taskSession = tab.taskSession?.toSnapshot(),
                )
            },
            state.activeTabNumber,
            nextTabNumber,
            state.selectedStorageType,
        )
    }

    private fun String.toTabTitle(): String {
        val words = trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        return words
            .take(MaxTitleWords)
            .joinToString(separator = " ")
            .ifBlank { ChatTab.NewTitle }
    }

    private companion object {
        const val MaxTitleWords = 5
        const val ProfileInterviewQuestionsCount = 5
        val EmptyProfileTemplate = """
            Язык и обращение:
            Детальность ответов:
            Стиль общения:
            Роль и проект:
            Технические рамки:
        """.trimIndent()
        val EmptyInvariantsTemplate = """
            # Формат строки:
            # category | enabled | statement | rationale
            # categories: architecture, technical_decision, stack_constraint, business_rule, process, security, other
        """.trimIndent()
    }
}

private fun formatProfileError(exception: Throwable): String =
    exception.message ?: exception::class.simpleName ?: "unknown"

private fun initialInvariantsChatMessages(): List<InvariantsChatMessage> =
    listOf(
        InvariantsChatMessage(
            role = InvariantsChatRole.Assistant,
            text = InvariantsCollectionQuestions.joinToString(separator = "\n") { question -> "- $question" },
        ),
    )

private fun List<InvariantsChatMessage>.toCollectionMessages(): List<InvariantCollectionMessage> =
    map { message ->
        InvariantCollectionMessage(
            role = when (message.role) {
                InvariantsChatRole.Assistant -> InvariantCollectionRole.Assistant
                InvariantsChatRole.User -> InvariantCollectionRole.User
            },
            text = message.text,
        )
    }

private fun List<AssistantInvariant>.toDraftText(): String =
    joinToString(separator = "\n") { invariant ->
        listOf(
            invariant.category.storageValue,
            invariant.enabled.toString(),
            invariant.statement,
            invariant.rationale,
        ).joinToString(separator = " | ")
    }

private fun String.toInvariants(): List<AssistantInvariant> =
    lineSequence()
        .map { it.trim() }
        .filter { line -> line.isNotEmpty() && !line.startsWith("#") }
        .mapIndexedNotNull { index, line ->
            val parts = line.split("|").map { it.trim() }
            val category = parts.firstOrNull()?.toInvariantCategory()
            val parsed = when {
                category != null && parts.size >= 3 -> ParsedInvariantLine(
                    category = category,
                    enabled = parts[1].toEnabledFlag(),
                    statement = parts[2],
                    rationale = parts.drop(3).joinToString(separator = " | "),
                )
                category != null && parts.size >= 2 -> ParsedInvariantLine(
                    category = category,
                    enabled = true,
                    statement = parts[1],
                    rationale = parts.drop(2).joinToString(separator = " | "),
                )
                else -> ParsedInvariantLine(
                    category = InvariantCategory.Other,
                    enabled = true,
                    statement = line,
                    rationale = "",
                )
            }
            parsed.statement
                .takeIf { it.isNotBlank() }
                ?.let { statement ->
                    AssistantInvariant(
                        id = "invariant-${index + 1}",
                        category = parsed.category,
                        statement = statement,
                        rationale = parsed.rationale,
                        enabled = parsed.enabled,
                    )
                }
        }
        .toList()

private data class ParsedInvariantLine(
    val category: InvariantCategory,
    val enabled: Boolean,
    val statement: String,
    val rationale: String,
)

private fun String.toEnabledFlag(): Boolean =
    when (lowercase()) {
        "false", "0", "disabled", "off", "no", "нет", "выключен", "выключено" -> false
        else -> true
    }

private val InvariantCategory.storageValue: String
    get() = when (this) {
        InvariantCategory.Architecture -> "architecture"
        InvariantCategory.TechnicalDecision -> "technical_decision"
        InvariantCategory.StackConstraint -> "stack_constraint"
        InvariantCategory.BusinessRule -> "business_rule"
        InvariantCategory.Process -> "process"
        InvariantCategory.Security -> "security"
        InvariantCategory.Other -> "other"
    }

private fun String.toInvariantCategory(): InvariantCategory? =
    when (this) {
        "architecture" -> InvariantCategory.Architecture
        "technical_decision" -> InvariantCategory.TechnicalDecision
        "stack_constraint" -> InvariantCategory.StackConstraint
        "business_rule" -> InvariantCategory.BusinessRule
        "process" -> InvariantCategory.Process
        "security" -> InvariantCategory.Security
        "other" -> InvariantCategory.Other
        else -> null
    }

private val InvariantsCollectionQuestions = listOf(
    "Какая архитектура обязательна для этого проекта и какие архитектурные подходы нельзя предлагать?",
    "Какие технические решения уже приняты и должны сохраняться?",
    "Какие ограничения стека, платформ, зависимостей, кодстайла, тестов и процесса обязательны?",
    "Какие бизнес-правила, безопасность и продуктовые ограничения нельзя нарушать?",
    "Какие решения ассистенту прямо запрещено предлагать даже как альтернативу?",
)

private fun taskStageChatId(
    tabNumber: Int,
    stage: TaskState,
): Int =
    tabNumber * TaskStageChatIdMultiplier + stage.ordinal + 1

private fun buildStageSystemPrompt(stage: TaskState): String =
    when (stage) {
        TaskState.Planning -> """
            You are the isolated planning agent for a coding assistant task.
            Work only on the planning stage. Gather missing information, choose technologies from the project context, and produce an implementable plan.
            Do not claim implementation is done.
        """.trimIndent()

        TaskState.Execution -> """
            You are the isolated execution agent for a coding assistant task.
            Work only from the accepted planning input. Describe concrete implementation work, decisions, changed behavior, and any blockers.
            Do not validate the result as final; validation is handled by another stage.
        """.trimIndent()

        TaskState.Validation -> """
            You are the isolated validation agent for a coding assistant task.
            Check the execution result against the accepted plan. Focus on build, tests, regressions, and remaining risks.
            Produce a validation result that the orchestrator can accept or send back for revision.
        """.trimIndent()

        TaskState.Done -> """
            You are the isolated final report agent for a coding assistant task.
            Summarize the accepted plan, execution result, validation result, and final outcome.
            Do not start new work. Produce the final done-stage response.
        """.trimIndent()
    }.withStageResultProtocol()

private fun String.withStageResultProtocol(): String =
    buildString {
        appendLine(this@withStageResultProtocol)
        appendLine()
        appendLine("Stage result protocol:")
        appendLine("Every response may include exactly one structured block when you need to report stage state to the application.")
        appendLine("If you are asking the user a question or need more information, use status needs_user_input.")
        appendLine("If you are still working or discussing without a final stage artifact, use status in_progress or omit the block.")
        appendLine("Use status completed only when the final result for this stage is ready for the user's accept/reject decision.")
        appendLine("Use status blocked only when this stage cannot be completed under the current constraints.")
        appendLine(TaskStageResultStart)
        appendLine("status: in_progress|needs_user_input|completed|blocked")
        appendLine("output: final result only for completed/blocked")
        appendLine("question: user question only for needs_user_input")
        appendLine("reason: short reason or blocker")
        appendLine(TaskStageResultEnd)
        appendLine("Do not mark clarifying questions as completed. Do not use completed until you are confident the stage is finished.")
    }.trimEnd()

private fun buildStageInput(
    context: TaskContext,
    stage: TaskState,
    previousOutput: String?,
    additionalInput: String?,
): String =
    buildString {
        appendLine("Task: ${context.task}")
        appendLine("Current stage: ${stage.title}")
        appendLine("Step: ${stage.ordinal + 1}/${TaskState.entries.size}")
        if (context.plan.isNotEmpty()) {
            appendLine()
            appendLine("Accepted plan:")
            context.plan.forEach { item -> appendLine("- $item") }
        }
        if (context.done.isNotEmpty()) {
            appendLine()
            appendLine("Already done:")
            context.done.forEach { item -> appendLine("- $item") }
        }
        previousOutput?.takeIf { it.isNotBlank() }?.let { output ->
            appendLine()
            appendLine("Previous stage output:")
            appendLine(output)
        }
        additionalInput?.takeIf { it.isNotBlank() }?.let { input ->
            appendLine()
            appendLine("Additional user input:")
            appendLine(input)
        }
        appendLine()
        appendLine("Work only on the ${stage.title} stage. Use the stage result protocol only when you need to report whether this stage is in progress, waiting for the user, completed, or blocked.")
    }

private fun buildRejectionAnalysisPrompt(
    rejection: TaskStageRejection,
    userClarification: String?,
): String =
    buildString {
        val isAcceptedBlockedResult = userClarification
            ?.contains("accepted the blocked result", ignoreCase = true) == true
        appendLine("TASK_REJECTION_ANALYSIS")
        if (isAcceptedBlockedResult) {
            appendLine("The user accepted that the ${rejection.stage.title} stage is blocked and cannot be completed as-is.")
        } else {
            appendLine("The user rejected the result of the ${rejection.stage.title} stage.")
        }
        appendLine("You are the orchestrator. Analyze the stage outcome and choose the next FSM action.")
        appendLine()
        appendLine("Return exactly one structured decision block:")
        appendLine(RejectionDecisionStart)
        appendLine("action: retry_current|return_previous|ask_user")
        appendLine("reason: concise reason")
        appendLine("additional_input: data to pass to the selected stage")
        appendLine("question: question for the user when action is ask_user")
        appendLine(RejectionDecisionEnd)
        appendLine()
        appendLine("Rules:")
        appendLine("- Use retry_current when the same stage can be improved with more context.")
        appendLine("- Use return_previous only when the previous stage must be refined first.")
        appendLine("- Use ask_user when the rejection reason is unclear.")
        if (rejection.stage.previous() == null) {
            appendLine("- The current stage has no previous stage, so do not use return_previous.")
        }
        appendLine("- Do not move to the next stage after rejection.")
        appendLine()
        appendLine("Task: ${rejection.context.task}")
        appendLine("Rejected stage: ${rejection.stage.title}")
        rejection.proposedNextStage?.let { stage ->
            appendLine("Rejected proposed next stage: ${stage.title}")
        }
        if (rejection.context.plan.isNotEmpty()) {
            appendLine()
            appendLine("Accepted plan:")
            rejection.context.plan.forEach { item -> appendLine("- $item") }
        }
        if (rejection.context.done.isNotEmpty()) {
            appendLine()
            appendLine("Already done:")
            rejection.context.done.forEach { item -> appendLine("- $item") }
        }
        appendLine()
        appendLine(if (isAcceptedBlockedResult) "Blocked stage output:" else "Rejected stage output:")
        appendLine(rejection.rejectedOutput)
        rejection.proposedInputForTarget?.takeIf { it.isNotBlank() }?.let { input ->
            appendLine()
            appendLine("Input that would have been sent to the next stage:")
            appendLine(input)
        }
        userClarification?.takeIf { it.isNotBlank() }?.let { clarification ->
            appendLine()
            appendLine("User clarification after orchestrator question:")
            appendLine(clarification)
        }
    }

private fun buildRejectedStageAdditionalInput(
    rejection: TaskStageRejection,
    reason: String,
    additionalInput: String,
): String =
    buildString {
        appendLine("The user rejected the ${rejection.stage.title} stage result.")
        reason.takeIf { it.isNotBlank() }?.let { appendLine("Orchestrator reason: $it") }
        additionalInput.takeIf { it.isNotBlank() }?.let {
            appendLine()
            appendLine("Additional data for this retry/refinement:")
            appendLine(it)
        }
        appendLine()
        appendLine("Rejected ${rejection.stage.title} output:")
        appendLine(rejection.rejectedOutput)
    }

private fun buildTaskOrchestratorRuntimePrompt(
    taskSession: TaskModeSession,
    allowCommands: Boolean = true,
): String? {
    val context = taskSession.context ?: return null
    val availability = TaskStateMachine().allowedActions(context, taskSession.toMachineRuntimeState())
    return buildString {
        appendLine("[TASK_STATE_MACHINE_ORCHESTRATOR_CONTEXT]")
        appendLine("You are the orchestrator chat for an active FSM: Finite State Machine / конечный автомат состояний.")
        appendLine("Treat this block as authoritative runtime state. The code reducer applies transitions; do not claim a transition was applied unless the state below says so.")
        appendLine("You are an interface to the code-owned FSM, not an executor for planning/execution/validation/done deliverables.")
        appendLine()
        appendLine("Task: ${context.task}")
        appendLine("Current state: ${context.state.title}")
        appendLine("Step: ${context.step}/${context.total}")
        appendLine("Expected action: ${context.expectedAction}")
        appendLine("Current work: ${context.current}")
        if (context.plan.isNotEmpty()) {
            appendLine()
            appendLine("Accepted plan:")
            context.plan.forEach { item -> appendLine("- $item") }
        }
        if (context.done.isNotEmpty()) {
            appendLine()
            appendLine("Done so far:")
            context.done.forEach { item -> appendLine("- ${item.excerpt()}") }
        }
        taskSession.pendingTransition?.let { proposal ->
            appendLine()
            appendLine("Pending transition: ${proposal.from.title} -> ${proposal.to.title}")
            appendLine("Transition reason: ${proposal.reason}")
        }
        taskSession.pendingRejection?.let { rejection ->
            appendLine()
            appendLine("Pending rejection:")
            appendLine("- rejected stage: ${rejection.stage.title}")
            rejection.reason?.takeIf { it.isNotBlank() }?.let { appendLine("- reason: $it") }
            rejection.question?.takeIf { it.isNotBlank() }?.let { appendLine("- question to user: $it") }
            appendLine("- rejected output: ${rejection.rejectedOutput.excerpt()}")
        }
        if (taskSession.stageAgents.isNotEmpty()) {
            appendLine()
            appendLine("Stage agents:")
            taskSession.stageAgents.sortedBy { it.session.state.ordinal }.forEach { agent ->
                val session = agent.session
                val output = if (taskSession.canExposeStageOutputToOrchestrator(session)) {
                    session.output.orEmpty().excerpt()
                } else if (session.isReadyForTransition) {
                    "waiting for explicit user accept/reject decision"
                } else {
                    ""
                }
                appendLine(
                    "- ${session.state.title}: reached=${session.isReached}, status=${session.resultStatus}, " +
                        "ready=${session.isReadyForTransition}, input=${session.input.excerpt()}, output=$output",
                )
            }
        }
        appendLine()
        appendLine("Current allowed actions:")
        availability.allowed.sortedBy { it.ordinal }.forEach { action ->
            appendLine("- ${action.name}")
        }
        appendLine()
        appendLine("Forbidden actions:")
        availability.forbiddenReasons.entries.sortedBy { it.key.ordinal }.forEach { (action, reason) ->
            appendLine("- ${action.name}: $reason")
        }
        if (allowCommands) {
            appendLine()
            appendLine("Structured command protocol:")
            appendLine("Use it only when you want the code to apply an allowed FSM action after your reply.")
            appendLine(TaskOrchestratorCommandStart)
            appendLine("action: discuss_only|delegate_current_stage|accept_current_stage|reject_current_stage|request_stage_transition|ask_user")
            appendLine("target_stage: planning|execution|validation|done")
            appendLine("reason: concise reason")
            appendLine("input_for_stage: exact input for the stage agent when action is delegate_current_stage")
            appendLine("user_clarification: user's rejection clarification when action is reject_current_stage")
            appendLine(TaskOrchestratorCommandEnd)
        } else {
            appendLine()
            appendLine("This is a follow-up response after the code-owned FSM processed a previous command.")
            appendLine("Do not emit a ${TaskOrchestratorCommandStart} block in this response.")
            appendLine("Only explain the FSM result to the user using the current state above.")
        }
        appendLine()
        appendLine("Rules:")
        appendLine("- The user may talk to you independently from stage agents.")
        appendLine("- Do not say that you accepted, rejected, returned, moved, switched state, or changed the FSM unless a task state event above already records that code applied it.")
        appendLine("- If you want the code to perform an FSM action, say that you are requesting the code action and return the structured command.")
        appendLine("- Ordinary questions, facts, explanations, and discussion must be discuss_only and must not change the FSM.")
        appendLine("- Never perform the deliverable of the current or future stage yourself; delegate stage work with a structured command.")
        appendLine("- Delegation is valid only for the current FSM stage and only when DelegateCurrentStage is allowed above.")
        appendLine("- Current stage output is not available to you until the user explicitly clicks accept or reject.")
        appendLine("- Forward movement is allowed only after the user accepts the current stage result.")
        appendLine("- Ordinary chat text such as 'accept', 'continue', or 'go next' must not apply transitions.")
        appendLine("- After rejection, decide whether to retry the current stage, return to the previous stage, or ask the user for clarification.")
        appendLine("- If the user requests an action forbidden above, explain why it is blocked by the FSM state.")
        appendLine("- Keep user-facing answers consistent with the current FSM state and stage-agent outputs.")
        append("[/TASK_STATE_MACHINE_ORCHESTRATOR_CONTEXT]")
    }
}

private fun buildTaskStartedEvent(context: TaskContext): String =
    buildString {
        appendLine("Task State Machine started.")
        appendLine("Task: ${context.task}")
        appendLine("Current stage: ${context.state.title}")
        append("Expected action: ${context.expectedAction}")
    }

private fun buildTransitionAcceptedEvent(proposal: TaskTransitionProposal): String =
    buildString {
        appendLine("Transition accepted by user.")
        appendLine("From: ${proposal.from.title}")
        appendLine("To: ${proposal.to.title}")
        append("Reason: ${proposal.reason}")
    }

private fun buildStageRejectedEvent(rejection: TaskStageRejection): String =
    buildString {
        appendLine("Stage result rejected by user.")
        appendLine("Rejected stage: ${rejection.stage.title}")
        rejection.proposedNextStage?.let { appendLine("Canceled next stage: ${it.title}") }
        append("Next action: orchestrator decision")
    }

private fun buildRejectionClarificationEvent(rejection: TaskStageRejection): String =
    buildString {
        appendLine("User clarification received for rejected stage.")
        appendLine("Rejected stage: ${rejection.stage.title}")
        append("Next action: orchestrator decision")
    }

private fun buildRejectedStageDecisionEvent(
    rejection: TaskStageRejection,
    target: TaskState,
    reason: String,
): String =
    buildString {
        if (target == rejection.stage) {
            appendLine("Orchestrator decision: retry current stage.")
        } else {
            appendLine("Orchestrator decision: return to previous stage.")
        }
        appendLine("Rejected stage: ${rejection.stage.title}")
        appendLine("Target stage: ${target.title}")
        reason.takeIf { it.isNotBlank() }?.let { append("Reason: $it") }
    }.trimEnd()

private fun buildRejectionQuestionEvent(
    rejection: TaskStageRejection,
    reason: String,
): String =
    buildString {
        appendLine("Orchestrator needs user clarification before choosing the next stage.")
        appendLine("Rejected stage: ${rejection.stage.title}")
        reason.takeIf { it.isNotBlank() }?.let { append("Reason: $it") }
    }.trimEnd()

private fun buildStageCompletedEvent(
    stage: TaskState,
    pendingTransition: TaskTransitionProposal?,
    completedContext: TaskContext,
): String =
    buildString {
        appendLine("Stage completed: ${stage.title}")
        appendLine("Expected action: ${completedContext.expectedAction}")
        if (pendingTransition != null) {
            appendLine("Pending transition: ${pendingTransition.from.title} -> ${pendingTransition.to.title}")
            append("Waiting for user decision.")
        } else if (stage == TaskState.Done) {
            append("Task State Machine completed.")
        } else {
            append("No pending transition.")
        }
    }

private fun buildStageResultAcceptedEvent(
    stage: TaskState,
    output: String,
    pendingTransition: TaskTransitionProposal?,
): String =
    buildString {
        appendLine("Stage result accepted by user.")
        appendLine("Stage: ${stage.title}")
        if (pendingTransition != null) {
            appendLine("Transition: ${pendingTransition.from.title} -> ${pendingTransition.to.title}")
            appendLine("Reason: ${pendingTransition.reason}")
        } else {
            appendLine("Task State Machine completed.")
        }
        appendLine()
        appendLine("Accepted stage output:")
        append(output.excerpt())
    }

private fun buildBlockedStageAcceptedEvent(
    stage: TaskState,
    result: TaskStageResult,
): String =
    buildString {
        appendLine("Blocked stage result accepted by user.")
        appendLine("Stage: ${stage.title}")
        result.reason.takeIf { it.isNotBlank() }?.let { appendLine("Reason: $it") }
        appendLine("The orchestrator will analyze how to proceed.")
    }.trimEnd()

private fun buildTaskOrchestratorDelegatedStageEvent(
    stage: TaskState,
    reason: String,
): String =
    buildString {
        appendLine("Orchestrator delegated work to the current stage agent.")
        appendLine("Target stage: ${stage.title}")
        reason.takeIf { it.isNotBlank() }?.let { append("Reason: $it") }
    }.trimEnd()

private fun buildTaskMachineFollowUpPrompt(
    command: TaskOrchestratorCommand,
    result: String,
    taskSession: TaskModeSession,
): String =
    buildString {
        appendLine("This is a Task State Machine result, not a user message.")
        appendLine("The previous assistant response contained an FSM command. The code-owned FSM processed it.")
        appendLine()
        appendLine("Processed command:")
        appendLine("- action: ${command.actionName}")
        command.targetStage?.let { appendLine("- target_stage: ${it.title}") }
        command.reason.takeIf { it.isNotBlank() }?.let { appendLine("- reason: $it") }
        appendLine()
        appendLine("FSM result:")
        appendLine(result)
        taskSession.context?.let { context ->
            appendLine()
            appendLine("Current FSM snapshot after processing:")
            appendLine("- state: ${context.state.title}")
            appendLine("- expected_action: ${context.expectedAction}")
            appendLine("- current: ${context.current}")
            taskSession.pendingTransition?.let { proposal ->
                appendLine("- pending_transition: ${proposal.from.title} -> ${proposal.to.title}")
            }
            taskSession.pendingRejection?.let { rejection ->
                appendLine("- pending_rejection_stage: ${rejection.stage.title}")
                rejection.question?.takeIf { it.isNotBlank() }?.let { appendLine("- pending_question: $it") }
            }
        }
        appendLine()
        append("Explain this result to the user. Do not emit another FSM command block.")
    }

private fun buildTaskOrchestratorCommandBlockedEvent(
    command: TaskOrchestratorCommand,
    currentStage: TaskState?,
    reason: String,
): String =
    buildString {
        appendLine("Orchestrator command blocked by Task State Machine.")
        appendLine("Requested action: ${command.actionName}")
        command.targetStage?.let { appendLine("Requested stage: ${it.title}") }
        currentStage?.let { appendLine("Current stage: ${it.title}") }
        append("Reason: $reason")
    }

private fun TaskModeSession.blockedAcceptReason(): String =
    when {
        context == null -> "Task State Machine is not started."
        currentFinalStageResult(context) == null ->
            "There is no completed or blocked stage result waiting for acceptance."
        else -> "Current FSM state does not allow accepting a transition."
    }

private fun TaskModeSession.blockedRejectReason(): String =
    when {
        context == null -> "Task State Machine is not started."
        currentFinalStageResult(context) == null ->
            "There is no completed or blocked stage result waiting for rejection."
        else -> "Current FSM state does not allow rejecting a stage."
    }

private fun TaskModeSession.blockedTransitionRequestReason(): String {
    val context = context ?: return "Task State Machine is not started."
    return when (context.expectedAction) {
        TaskExpectedAction.UserConfirmation ->
            "Direct stage navigation from chat is not allowed. Accept or reject the current ${context.state.title} result first."
        TaskExpectedAction.AgentWork ->
            "Direct stage navigation from chat is not allowed while the ${context.state.title} stage is active."
        TaskExpectedAction.OrchestratorDecision ->
            "Direct stage navigation from chat is not allowed while the orchestrator analyzes the rejected ${context.state.title} stage."
        TaskExpectedAction.UserPrompt ->
            "Direct stage navigation from chat is not allowed while the FSM is waiting for rejection clarification."
        TaskExpectedAction.Completed ->
            "Direct stage navigation from chat is not allowed because the task is completed."
    }
}

private fun TaskModeSession.currentFinalStageResult(context: TaskContext): TaskStageResult? {
    val session = stageAgents
        .firstOrNull { it.session.state == context.state }
        ?.session
        ?: return null
    if (!session.isReadyForTransition) {
        return null
    }
    val output = session.output.orEmpty()
    if (output.isBlank()) {
        return null
    }
    return TaskStageResult(
        status = session.resultStatus,
        output = output,
        question = session.resultQuestion,
        reason = session.resultReason,
    )
}

private fun TaskModeSession.canExposeStageOutputToOrchestrator(session: TaskStageSession): Boolean {
    val context = context ?: return false
    return session.output != null &&
        (
            session.state != context.state ||
                context.expectedAction != TaskExpectedAction.AgentWork ||
                pendingRejection?.stage == session.state
            )
}

private fun TaskModeSession.toMachineRuntimeState(): TaskMachineRuntimeState {
    val currentStage = context?.state
    val currentAgent = stageAgents.firstOrNull { it.session.state == currentStage }
    return TaskMachineRuntimeState(
        isCurrentStageLoading = currentAgent?.viewModel?.state?.isLoading == true,
        hasPendingTransition = pendingTransition != null || currentAgent?.session?.isReadyForTransition == true,
        hasPendingRejection = pendingRejection != null,
    )
}

private sealed interface TaskOrchestratorCommand {
    data object DiscussOnly : TaskOrchestratorCommand

    data class AcceptCurrentStage(
        val reason: String,
    ) : TaskOrchestratorCommand

    data class RejectCurrentStage(
        val reason: String,
        val userClarification: String,
    ) : TaskOrchestratorCommand

    data class DelegateCurrentStage(
        val targetStage: TaskState,
        val reason: String,
        val inputForStage: String,
    ) : TaskOrchestratorCommand

    data class RequestStageTransition(
        val targetStage: TaskState?,
        val reason: String,
    ) : TaskOrchestratorCommand

    data class AskUser(
        val reason: String,
    ) : TaskOrchestratorCommand
}

private val TaskOrchestratorCommand.actionName: String
    get() = when (this) {
        TaskOrchestratorCommand.DiscussOnly -> "discuss_only"
        is TaskOrchestratorCommand.AcceptCurrentStage -> "accept_current_stage"
        is TaskOrchestratorCommand.RejectCurrentStage -> "reject_current_stage"
        is TaskOrchestratorCommand.DelegateCurrentStage -> "delegate_current_stage"
        is TaskOrchestratorCommand.RequestStageTransition -> "request_stage_transition"
        is TaskOrchestratorCommand.AskUser -> "ask_user"
    }

private val TaskOrchestratorCommand.targetStage: TaskState?
    get() = when (this) {
        is TaskOrchestratorCommand.DelegateCurrentStage -> targetStage
        is TaskOrchestratorCommand.RequestStageTransition -> targetStage
        else -> null
    }

private val TaskOrchestratorCommand.reason: String
    get() = when (this) {
        TaskOrchestratorCommand.DiscussOnly -> ""
        is TaskOrchestratorCommand.AcceptCurrentStage -> reason
        is TaskOrchestratorCommand.RejectCurrentStage -> reason
        is TaskOrchestratorCommand.DelegateCurrentStage -> reason
        is TaskOrchestratorCommand.RequestStageTransition -> reason
        is TaskOrchestratorCommand.AskUser -> reason
    }

private fun parseTaskStageResult(output: String): TaskStageResult {
    val block = output
        .substringAfter(TaskStageResultStart, missingDelimiterValue = "")
        .substringBefore(TaskStageResultEnd, missingDelimiterValue = "")
        .takeIf { it.isNotBlank() }
        ?: return TaskStageResult(status = TaskStageResultStatus.InProgress)
    val fields = block.toStructuredFields(StageResultFieldNames)
    val status = fields["status"]?.trim()?.lowercase()
        ?.toTaskStageResultStatusOrNull()
        ?: return TaskStageResult(status = TaskStageResultStatus.InProgress)
    val result = TaskStageResult(
        status = status,
        output = fields["output"].orEmpty().trim(),
        question = fields["question"].orEmpty().trim(),
        reason = fields["reason"].orEmpty().trim(),
    )
    return when (status) {
        TaskStageResultStatus.Completed ->
            result.takeIf { it.output.isNotBlank() }
                ?: TaskStageResult(status = TaskStageResultStatus.InProgress)
        TaskStageResultStatus.Blocked ->
            result.copy(output = result.output.ifBlank { result.reason })
                .takeIf { it.output.isNotBlank() }
                ?: TaskStageResult(status = TaskStageResultStatus.InProgress)
        TaskStageResultStatus.NeedsUserInput,
        TaskStageResultStatus.InProgress -> result
    }
}

private fun TaskStageResult.finalOutput(): String? =
    when (status) {
        TaskStageResultStatus.Completed,
        TaskStageResultStatus.Blocked -> output.takeIf { it.isNotBlank() }
        TaskStageResultStatus.InProgress,
        TaskStageResultStatus.NeedsUserInput -> null
    }

private fun parseTaskOrchestratorCommand(output: String): TaskOrchestratorCommand {
    val block = output
        .substringAfter(TaskOrchestratorCommandStart, missingDelimiterValue = "")
        .substringBefore(TaskOrchestratorCommandEnd, missingDelimiterValue = "")
        .takeIf { it.isNotBlank() }
        ?: return TaskOrchestratorCommand.DiscussOnly
    val fields = block.toTaskCommandFields()
    val reason = fields["reason"].orEmpty()
    return when (fields["action"]?.lowercase()) {
        "discuss_only" -> TaskOrchestratorCommand.DiscussOnly
        "accept_current_stage" -> TaskOrchestratorCommand.AcceptCurrentStage(reason = reason)
        "reject_current_stage" -> TaskOrchestratorCommand.RejectCurrentStage(
            reason = reason,
            userClarification = fields["user_clarification"].orEmpty(),
        )
        "delegate_current_stage" -> {
            val targetStage = fields["target_stage"]?.toTaskStateOrNull()
                ?: return TaskOrchestratorCommand.DiscussOnly
            val inputForStage = fields["input_for_stage"].orEmpty()
                .takeIf { it.isNotBlank() }
                ?: return TaskOrchestratorCommand.DiscussOnly
            TaskOrchestratorCommand.DelegateCurrentStage(
                targetStage = targetStage,
                reason = reason,
                inputForStage = inputForStage,
            )
        }

        "request_stage_transition" -> TaskOrchestratorCommand.RequestStageTransition(
            targetStage = fields["target_stage"]?.toTaskStateOrNull(),
            reason = reason,
        )
        "ask_user" -> TaskOrchestratorCommand.AskUser(reason = reason)
        else -> TaskOrchestratorCommand.DiscussOnly
    }
}

private fun String.toTaskCommandFields(): Map<String, String> =
    lineSequence()
        .mapNotNull { line ->
            val separatorIndex = line.indexOf(':')
            if (separatorIndex <= 0) {
                null
            } else {
                line.take(separatorIndex).trim().lowercase() to
                    line.drop(separatorIndex + 1).trim()
            }
        }
        .toMap()

private fun String.toStructuredFields(fieldNames: Set<String>): Map<String, String> {
    val result = linkedMapOf<String, StringBuilder>()
    var currentKey: String? = null
    lineSequence().forEach { line ->
        val separatorIndex = line.indexOf(':')
        val key = if (separatorIndex > 0) {
            line.take(separatorIndex).trim().lowercase()
        } else {
            ""
        }
        if (key in fieldNames) {
            currentKey = key
            result.getOrPut(key) { StringBuilder() }
                .append(line.drop(separatorIndex + 1).trimStart())
        } else {
            currentKey?.let { activeKey ->
                result.getOrPut(activeKey) { StringBuilder() }
                    .appendLine()
                    .append(line)
            }
        }
    }
    return result.mapValues { (_, value) -> value.toString().trim() }
}

private fun String.toTaskStateOrNull(): TaskState? =
    TaskState.entries.firstOrNull { stage -> stage.title == trim().lowercase() }

private fun String.toTaskStageResultStatusOrNull(): TaskStageResultStatus? =
    when (this) {
        "in_progress" -> TaskStageResultStatus.InProgress
        "needs_user_input" -> TaskStageResultStatus.NeedsUserInput
        "completed" -> TaskStageResultStatus.Completed
        "blocked" -> TaskStageResultStatus.Blocked
        else -> null
    }

private fun parseTaskOrchestratorDecision(output: String): TaskOrchestratorDecision? {
    val block = output
        .substringAfter(RejectionDecisionStart, missingDelimiterValue = "")
        .substringBefore(RejectionDecisionEnd, missingDelimiterValue = "")
        .takeIf { it.isNotBlank() }
        ?: return null
    val fields = block
        .lineSequence()
        .mapNotNull { line ->
            val separatorIndex = line.indexOf(':')
            if (separatorIndex <= 0) {
                null
            } else {
                line.take(separatorIndex).trim().lowercase() to
                    line.drop(separatorIndex + 1).trim()
            }
        }
        .toMap()

    val reason = fields["reason"].orEmpty()
    return when (fields["action"]?.lowercase()) {
        "retry_current" -> TaskOrchestratorDecision.RetryCurrent(
            reason = reason,
            additionalInput = fields["additional_input"].orEmpty(),
        )

        "return_previous" -> TaskOrchestratorDecision.ReturnPrevious(
            reason = reason,
            additionalInput = fields["additional_input"].orEmpty(),
        )

        "ask_user" -> fields["question"]
            ?.takeIf { it.isNotBlank() }
            ?.let { question ->
                TaskOrchestratorDecision.AskUser(
                    reason = reason,
                    question = question,
                )
            }

        else -> null
    }
}

private fun defaultRejectionQuestion(stage: TaskState): String =
    "Почему вы отклонили результат этапа ${stage.title}? Что нужно исправить или уточнить?"

private fun extractPlanItems(output: String): List<String> {
    val lines = output
        .lineSequence()
        .map { line ->
            line.trim()
                .removePrefix("-")
                .removePrefix("*")
                .removePrefix("•")
                .trim()
        }
        .filter { it.isNotBlank() }
        .take(MaxExtractedPlanItems)
        .toList()
    return lines.ifEmpty { listOf(output.trim().take(MaxFallbackPlanChars)) }
}

private fun String.excerpt(maxChars: Int = MaxRuntimeBriefingExcerptChars): String {
    val normalized = lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString(separator = " ")
    return if (normalized.length <= maxChars) {
        normalized
    } else {
        normalized.take(maxChars).trimEnd() + "..."
    }
}

private fun ChatViewState.lastAssistantOutput(): String =
    messages
        .lastOrNull { it.role == ChatRole.Assistant && it.kind == ChatMessageKind.Regular }
        ?.content
        .orEmpty()

private fun ChatEvent.isPromptInputEvent(): Boolean =
    when (this) {
        is ChatEvent.PromptChanged,
        is ChatEvent.SystemPromptChanged,
        is ChatEvent.AttachmentSelected,
        is ChatEvent.AttachmentError,
        ChatEvent.AttachmentCleared,
        ChatEvent.SendClicked -> true

        else -> false
    }

private const val TaskStageChatIdMultiplier = 10
private const val MaxExtractedPlanItems = 12
private const val MaxFallbackPlanChars = 600
private const val MaxRuntimeBriefingExcerptChars = 800
private const val TaskStageResultStart = "[TASK_STAGE_RESULT]"
private const val TaskStageResultEnd = "[/TASK_STAGE_RESULT]"
private const val TaskOrchestratorCommandStart = "[TASK_ORCHESTRATOR_COMMAND]"
private const val TaskOrchestratorCommandEnd = "[/TASK_ORCHESTRATOR_COMMAND]"
private const val RejectionDecisionStart = "TASK_REJECTION_DECISION"
private const val RejectionDecisionEnd = "END_TASK_REJECTION_DECISION"
private val StageResultFieldNames = setOf("status", "output", "question", "reason")
