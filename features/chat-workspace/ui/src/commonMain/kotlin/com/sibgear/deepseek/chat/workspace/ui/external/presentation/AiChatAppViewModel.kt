package com.sibgear.deepseek.chat.workspace.ui.external.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sibgear.deepseek.chat.domain.model.ChatMessage
import com.sibgear.deepseek.chat.domain.model.ChatMessageKind
import com.sibgear.deepseek.chat.domain.model.ChatRole
import com.sibgear.deepseek.chat.domain.model.TaskContext
import com.sibgear.deepseek.chat.domain.model.TaskExpectedAction
import com.sibgear.deepseek.chat.domain.model.TaskOrchestratorDecision
import com.sibgear.deepseek.chat.domain.model.TaskSessionSnapshot
import com.sibgear.deepseek.chat.domain.model.TaskStageRejection
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
import com.sibgear.deepseek.chat.workspace.ui.external.model.StorageSwitchResult
import com.sibgear.deepseek.chat.workspace.ui.external.model.TaskModeSession
import com.sibgear.deepseek.chat.workspace.ui.external.model.TaskStageAgent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AiChatAppViewModel(
    private val coroutineScope: CoroutineScope,
    private val createChatViewModel: (tabNumber: Int, storageType: ChatStorageType) -> ChatViewModel,
    private val createTaskStageChatViewModel: (
        chatId: Int,
        storageType: ChatStorageType,
        systemPrompt: String,
        initialPrompt: String,
    ) -> ChatViewModel,
    private val createInitialTabTitle: (tabNumber: Int) -> String = { ChatTab.NewTitle },
    private val switchStorage: (
        storageType: ChatStorageType,
        currentTabs: List<ChatTab>,
        activeTabNumber: Int,
        nextTabNumber: Int,
    ) -> StorageSwitchResult,
    initialTabNumbers: List<Int> = emptyList(),
    initialTaskSessionsByTab: Map<Int, TaskSessionSnapshot> = emptyMap(),
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
) {
    private val taskMachine = TaskStateMachine()
    private val initialNumbers = initialTabNumbers
        .filter { it > 0 }
        .distinct()
        .ifEmpty { listOf(1) }
    private val initialTaskSnapshots = initialTaskSessionsByTab
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
            AiChatAppEvent.TaskModeToggled -> toggleTaskMode()
            is AiChatAppEvent.TaskStageSelected -> selectTaskStage(event.stage)
            AiChatAppEvent.TaskTransitionAccepted -> acceptTaskTransition()
            AiChatAppEvent.TaskStageRejected -> rejectTaskStage()
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
    ): AiChatAppViewState {
        val tabs = tabNumbers.map { tabNumber ->
            createTab(
                number = tabNumber,
                storageType = storageType,
                taskSnapshot = taskSnapshotsByTab[tabNumber],
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
    ): ChatTab {
        val viewModel = createChatViewModel(number, storageType)
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
            else -> activeViewModel.onEvent(event)
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

        updateTab(activeTab.number) { tab ->
            tab.copy(taskSession = taskSession.copy(selectedStage = stage))
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
            activeTab.viewModel.setPrompt("")
            activeTab.viewModel.appendPersistentMessage(
                ChatMessage(role = ChatRole.User, content = prompt),
            ) {
                sendRejectionClarificationToOrchestrator(activeTab.number, prompt)
            }
        } else {
            activeTab.viewModel.sendPrompt(runtimeSystemPrompt = buildTaskOrchestratorRuntimePrompt(taskSession))
        }
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

    private fun acceptTaskTransition() {
        val activeTab = state.activeTab ?: return
        val taskSession = activeTab.taskSession ?: return
        val context = taskSession.context ?: return
        val proposal = taskSession.pendingTransition ?: return
        val nextContext = taskMachine.acceptTransition(context, proposal)
        val nextSession = taskSession.ensureStageAgent(
            tabNumber = activeTab.number,
            storageType = state.selectedStorageType,
            stage = proposal.to,
            input = proposal.inputForTarget,
        ).copy(
            context = nextContext,
            selectedStage = proposal.to,
            pendingTransition = null,
            pendingRejection = null,
        )

        updateTab(activeTab.number) { tab -> tab.copy(taskSession = nextSession) }
        appendTaskStateEvent(
            tabNumber = activeTab.number,
            content = buildTransitionAcceptedEvent(proposal),
        ) {
            sendStagePrompt(activeTab.number, proposal.to, proposal.inputForTarget)
        }
    }

    private fun rejectTaskStage() {
        val activeTab = state.activeTab ?: return
        val taskSession = activeTab.taskSession ?: return
        val context = taskSession.context ?: return
        val proposal = taskSession.pendingTransition ?: return
        if (activeTab.viewModel.state.isLoading) {
            return
        }
        val rejectedOutput = taskSession.stageAgents
            .firstOrNull { it.session.state == context.state }
            ?.session
            ?.output
            ?.takeIf { it.isNotBlank() }
            ?: return
        val rejectedContext = taskMachine.rejectStage(context)
        val rejection = TaskStageRejection(
            stage = context.state,
            rejectedOutput = rejectedOutput,
            context = context,
            proposedNextStage = proposal.to,
            proposedInputForTarget = proposal.inputForTarget,
        )

        updateTab(activeTab.number) { tab ->
            tab.copy(
                taskSession = taskSession.copy(
                    context = rejectedContext,
                    selectedStage = context.state,
                    pendingTransition = null,
                    pendingRejection = rejection,
                ),
            )
        }
        appendTaskStateEvent(
            tabNumber = activeTab.number,
            content = buildStageRejectedEvent(rejection),
        ) {
            sendRejectionAnalysisPrompt(
                tabNumber = activeTab.number,
                rejection = rejection,
                userClarification = null,
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
            )
        }
    }

    private fun sendRejectionAnalysisPrompt(
        tabNumber: Int,
        rejection: TaskStageRejection,
        userClarification: String?,
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
                    pendingTransition = null,
                    pendingRejection = rejection.copy(question = null),
                ),
            )
        }

        val updatedTaskSession = state.tabs.firstOrNull { it.number == tabNumber }?.taskSession ?: taskSession
        activeTab.viewModel.setPrompt(buildRejectionAnalysisPrompt(rejection, userClarification))
        activeTab.viewModel.sendPrompt(
            runtimeSystemPrompt = buildTaskOrchestratorRuntimePrompt(updatedTaskSession),
        ) { orchestratorState ->
            handleRejectionDecision(
                tabNumber = tabNumber,
                orchestratorOutput = orchestratorState.lastAssistantOutput(),
            )
        }
    }

    private fun handleRejectionDecision(
        tabNumber: Int,
        orchestratorOutput: String,
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
                    )
                } else {
                    runRejectedStageDecision(
                        tabNumber = tabNumber,
                        rejection = rejection,
                        target = previous,
                        reason = decision.reason,
                        additionalInput = decision.additionalInput,
                    )
                }
            }

            is TaskOrchestratorDecision.AskUser -> {
                askUserForRejectionDetails(
                    tabNumber = tabNumber,
                    rejection = rejection,
                    question = decision.question,
                    reason = decision.reason,
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
            sendStagePrompt(tabNumber, target, input)
        }
    }

    private fun askUserForRejectionDetails(
        tabNumber: Int,
        rejection: TaskStageRejection,
        question: String,
        reason: String,
    ) {
        val tab = state.tabs.firstOrNull { it.number == tabNumber } ?: return
        val taskSession = tab.taskSession ?: return
        val context = taskSession.context ?: return
        val waitingContext = taskMachine.awaitRejectionDetails(context)

        updateTab(tabNumber) { currentTab ->
            currentTab.copy(
                taskSession = taskSession.copy(
                    context = waitingContext,
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
            )
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
        when (event) {
            ChatEvent.SendClicked -> {
                agent.viewModel.syncRequestSettingsFrom(activeTab.viewModel.state)
                agent.viewModel.sendPrompt { stageState ->
                    completeTaskStage(
                        tabNumber = activeTab.number,
                        stage = agent.session.state,
                        output = stageState.lastAssistantOutput(),
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
                            isReached = true,
                            isReadyForTransition = false,
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
                    pendingTransition = null,
                    pendingRejection = null,
                ),
            )
        }
        agent.viewModel.sendPrompt { stageState ->
            completeTaskStage(
                tabNumber = tabNumber,
                stage = stage,
                output = stageState.lastAssistantOutput(),
            )
        }
    }

    private fun completeTaskStage(
        tabNumber: Int,
        stage: TaskState,
        output: String,
    ) {
        val tab = state.tabs.firstOrNull { it.number == tabNumber } ?: return
        val taskSession = tab.taskSession ?: return
        val context = taskSession.context ?: return
        val updatedAgents = taskSession.stageAgents.map { agent ->
            if (agent.session.state == stage) {
                agent.copy(
                    session = agent.session.copy(
                        output = output,
                        isReadyForTransition = output.isNotBlank(),
                    ),
                )
            } else {
                agent
            }
        }
        val shouldControlTransition = stage == context.state && output.isNotBlank()
        val completedContext = if (shouldControlTransition) {
            taskMachine.completeStage(
                context = context,
                output = output,
                plan = if (stage == TaskState.Planning) extractPlanItems(output) else context.plan,
            )
        } else {
            context
        }
        val pendingTransition = if (shouldControlTransition) {
            val nextStage = stage.next()
            nextStage?.let { target ->
                taskMachine.proposeTransition(
                    context = completedContext,
                    to = target,
                    reason = "${stage.title} completed",
                    inputForTarget = buildStageInput(
                        context = completedContext.copy(state = target, current = target.title),
                        stage = target,
                        previousOutput = output,
                        additionalInput = null,
                    ),
                )
            }
        } else {
            taskSession.pendingTransition
        }

        updateTab(tabNumber) { currentTab ->
            val currentSession = currentTab.taskSession ?: return@updateTab currentTab
            currentTab.copy(
                taskSession = currentSession.copy(
                    context = completedContext,
                    stageAgents = updatedAgents,
                    pendingTransition = pendingTransition,
                    pendingRejection = null,
                    selectedStage = stage,
                ),
            )
        }
        if (shouldControlTransition) {
            appendTaskStateEvent(
                tabNumber = tabNumber,
                content = buildStageCompletedEvent(
                    stage = stage,
                    pendingTransition = pendingTransition,
                    completedContext = completedContext,
                ),
            )
        }
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
    }
}

private fun formatProfileError(exception: Throwable): String =
    exception.message ?: exception::class.simpleName ?: "unknown"

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
            Do not claim implementation is done. End with a concise result that the orchestrator can pass to execution.
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
    }

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
        appendLine("Return only the result for the $stage stage.")
    }

private fun buildRejectionAnalysisPrompt(
    rejection: TaskStageRejection,
    userClarification: String?,
): String =
    buildString {
        appendLine("TASK_REJECTION_ANALYSIS")
        appendLine("The user rejected the result of the ${rejection.stage.title} stage.")
        appendLine("You are the orchestrator. Analyze why the stage was rejected and choose the next FSM action.")
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
        appendLine("Rejected stage output:")
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

private fun buildTaskOrchestratorRuntimePrompt(taskSession: TaskModeSession): String? {
    val context = taskSession.context ?: return null
    return buildString {
        appendLine("[TASK_STATE_MACHINE_ORCHESTRATOR_CONTEXT]")
        appendLine("You are the orchestrator chat for an active Task State Machine.")
        appendLine("Treat this block as authoritative runtime state. The code reducer applies transitions; do not claim a transition was applied unless the state below says so.")
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
                appendLine(
                    "- ${session.state.title}: reached=${session.isReached}, ready=${session.isReadyForTransition}, " +
                        "input=${session.input.excerpt()}, output=${session.output.orEmpty().excerpt()}",
                )
            }
        }
        appendLine()
        appendLine("Rules:")
        appendLine("- The user may talk to you independently from stage agents.")
        appendLine("- Forward movement is allowed only after the user accepts the current stage result.")
        appendLine("- After rejection, decide whether to retry the current stage, return to the previous stage, or ask the user for clarification.")
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
private const val RejectionDecisionStart = "TASK_REJECTION_DECISION"
private const val RejectionDecisionEnd = "END_TASK_REJECTION_DECISION"
