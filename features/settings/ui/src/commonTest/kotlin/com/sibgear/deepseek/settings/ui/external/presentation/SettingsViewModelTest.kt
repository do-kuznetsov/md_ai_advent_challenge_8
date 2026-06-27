package com.sibgear.deepseek.settings.ui.external.presentation

import com.sibgear.deepseek.assistant.memory.domain.model.AssistantInvariant
import com.sibgear.deepseek.assistant.memory.domain.model.InvariantCategory
import com.sibgear.deepseek.assistant.memory.domain.model.InvariantCollectionRole
import com.sibgear.deepseek.settings.ui.external.model.SettingsEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @Test
    fun settingsDialogOpensAndCloses() = runTest {
        val viewModel = SettingsViewModel(coroutineScope = this)

        viewModel.onEvent(SettingsEvent.SettingsDialogOpened)
        assertEquals(true, viewModel.state.isSettingsDialogOpen)

        viewModel.onEvent(SettingsEvent.SettingsDialogClosed)
        assertEquals(false, viewModel.state.isSettingsDialogOpen)
    }

    @Test
    fun profileSelectionClosesSettingsAndOpensProfileDialog() = runTest {
        val viewModel = SettingsViewModel(
            coroutineScope = this,
            loadProfile = { "Стиль: кратко" },
        )

        viewModel.onEvent(SettingsEvent.SettingsDialogOpened)
        viewModel.onEvent(SettingsEvent.ProfileDialogOpened)
        advanceUntilIdle()

        assertEquals(false, viewModel.state.isSettingsDialogOpen)
        assertEquals(true, viewModel.state.isProfileDialogOpen)
        assertEquals("Стиль: кратко", viewModel.state.profileDraft)
    }

    @Test
    fun invariantsSelectionClosesSettingsAndOpensInvariantsDialog() = runTest {
        val viewModel = SettingsViewModel(coroutineScope = this)

        viewModel.onEvent(SettingsEvent.SettingsDialogOpened)
        viewModel.onEvent(SettingsEvent.InvariantsDialogOpened)
        advanceUntilIdle()

        assertEquals(false, viewModel.state.isSettingsDialogOpen)
        assertEquals(true, viewModel.state.isInvariantsDialogOpen)
        assertTrue(viewModel.state.invariantsChatMessages.first().text.contains("Какая архитектура обязательна"))
    }

    @Test
    fun profileDialogLoadsAndSavesDraft() = runTest {
        var savedProfile = ""
        val viewModel = SettingsViewModel(
            coroutineScope = this,
            loadProfile = { "Язык: русский" },
            saveProfile = { text ->
                savedProfile = text
                text
            },
        )

        viewModel.onEvent(SettingsEvent.ProfileDialogOpened)
        advanceUntilIdle()
        assertEquals(true, viewModel.state.isProfileDialogOpen)
        assertEquals("Язык: русский", viewModel.state.profileDraft)

        viewModel.onEvent(SettingsEvent.ProfileDraftChanged("Стиль: рабочий"))
        viewModel.onEvent(SettingsEvent.ProfileSaved)
        advanceUntilIdle()

        assertEquals(false, viewModel.state.isProfileDialogOpen)
        assertEquals("Стиль: рабочий", savedProfile)
    }

    @Test
    fun invariantsDialogLoadsAndSavesDraft() = runTest {
        var savedInvariants = emptyList<AssistantInvariant>()
        val viewModel = SettingsViewModel(
            coroutineScope = this,
            loadInvariants = {
                listOf(
                    AssistantInvariant(
                        id = "invariant-1",
                        category = InvariantCategory.Architecture,
                        statement = "Use layered architecture",
                        rationale = "Accepted decision",
                    ),
                )
            },
            saveInvariants = { invariants ->
                savedInvariants = invariants
                invariants
            },
        )

        viewModel.onEvent(SettingsEvent.InvariantsDialogOpened)
        advanceUntilIdle()
        assertEquals(true, viewModel.state.isInvariantsDialogOpen)
        assertTrue(viewModel.state.invariantsDraft.contains("Use layered architecture"))

        viewModel.onEvent(
            SettingsEvent.InvariantsDraftChanged(
                "stack_constraint | true | Do not add Swing | Compose app",
            ),
        )
        viewModel.onEvent(SettingsEvent.InvariantsSaved)
        advanceUntilIdle()

        assertEquals(false, viewModel.state.isInvariantsDialogOpen)
        assertEquals(
            listOf(
                AssistantInvariant(
                    id = "invariant-1",
                    category = InvariantCategory.StackConstraint,
                    statement = "Do not add Swing",
                    rationale = "Compose app",
                ),
            ),
            savedInvariants,
        )
    }

    @Test
    fun invariantsChatAppliesDraftWithoutSaving() = runTest {
        var saveCount = 0
        val viewModel = SettingsViewModel(
            coroutineScope = this,
            updateInvariantsFromChat = { current, messages ->
                assertEquals(emptyList(), current)
                assertEquals(InvariantCollectionRole.Assistant, messages.first().role)
                assertTrue(messages.first().text.contains("Какая архитектура обязательна"))
                assertTrue(
                    messages.any { message ->
                        message.role == InvariantCollectionRole.User &&
                            message.text == "Запрещено раскрывать персональные данные клиентов."
                    },
                )
                listOf(
                    AssistantInvariant(
                        id = "invariant-1",
                        category = InvariantCategory.BusinessRule,
                        statement = "Never expose private customer data",
                    ),
                )
            },
            saveInvariants = { invariants ->
                saveCount += 1
                invariants
            },
        )

        viewModel.onEvent(SettingsEvent.InvariantsDialogOpened)
        advanceUntilIdle()
        assertTrue(viewModel.state.invariantsChatMessages.first().text.contains("Какая архитектура обязательна"))

        viewModel.onEvent(
            SettingsEvent.InvariantsChatInputChanged("Запрещено раскрывать персональные данные клиентов."),
        )
        viewModel.onEvent(SettingsEvent.InvariantsChatMessageSent)
        viewModel.onEvent(SettingsEvent.InvariantsApplied)
        advanceUntilIdle()

        assertEquals(0, saveCount)
        assertTrue(viewModel.state.invariantsDraft.contains("Never expose private customer data"))
    }
}
