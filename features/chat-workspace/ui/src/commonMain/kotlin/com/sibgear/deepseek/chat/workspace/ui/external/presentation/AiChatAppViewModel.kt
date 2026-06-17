package com.sibgear.deepseek.chat.workspace.ui.external.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sibgear.deepseek.chat.ui.external.model.ChatEvent
import com.sibgear.deepseek.chat.ui.external.presentation.ChatViewModel
import com.sibgear.deepseek.chat.workspace.ui.external.model.AiChatAppEvent
import com.sibgear.deepseek.chat.workspace.ui.external.model.AiChatAppViewState
import com.sibgear.deepseek.chat.workspace.ui.external.model.ChatStorageType
import com.sibgear.deepseek.chat.workspace.ui.external.model.ChatTab
import com.sibgear.deepseek.chat.workspace.ui.external.model.StorageSwitchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AiChatAppViewModel(
    private val coroutineScope: CoroutineScope,
    private val createChatViewModel: (tabNumber: Int, storageType: ChatStorageType) -> ChatViewModel,
    private val createInitialTabTitle: (tabNumber: Int) -> String = { ChatTab.NewTitle },
    private val switchStorage: (
        storageType: ChatStorageType,
        currentTabs: List<ChatTab>,
        activeTabNumber: Int,
        nextTabNumber: Int,
    ) -> StorageSwitchResult,
    initialTabNumbers: List<Int> = emptyList(),
    initialActiveTabNumber: Int? = null,
    initialNextTabNumber: Int? = null,
    initialStorageType: ChatStorageType = ChatStorageType.Json,
    private val storageDirectoryLabel: String,
    private val onWorkspaceChanged: (
        tabNumbers: List<Int>,
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
    private val initialNumbers = initialTabNumbers
        .filter { it > 0 }
        .distinct()
        .ifEmpty { listOf(1) }
    private var nextTabNumber = maxOf(
        initialNextTabNumber ?: ((initialNumbers.maxOrNull() ?: 0) + 1),
        (initialNumbers.maxOrNull() ?: 0) + 1,
    )

    var state by mutableStateOf(
        createInitialState(
            tabNumbers = initialNumbers,
            activeTabNumber = initialActiveTabNumber,
            storageType = initialStorageType,
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
    ): AiChatAppViewState {
        val tabs = tabNumbers.map { createTab(it, storageType) }
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
    ): ChatTab {
        val viewModel = createChatViewModel(number, storageType)
        viewModel.loadModels()

        return ChatTab(
            number = number,
            title = createInitialTabTitle(number),
            viewModel = viewModel,
        )
    }

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
                updateActiveTabTitleIfNeeded(activeViewModel.state.prompt)
                activeViewModel.sendPrompt()
            }
            else -> activeViewModel.onEvent(event)
        }
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
            state.tabs.map { it.number },
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
            Стиль:
            Формат:
            Ограничения:
            Предпочтения:
        """.trimIndent()
    }
}

private fun formatProfileError(exception: Throwable): String =
    exception.message ?: exception::class.simpleName ?: "unknown"
