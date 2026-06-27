package com.sibgear.deepseek.settings.ui.external.presentation

import com.sibgear.deepseek.assistant.memory.domain.model.AssistantInvariant
import com.sibgear.deepseek.assistant.memory.domain.model.InvariantCategory
import com.sibgear.deepseek.assistant.memory.domain.model.InvariantCollectionRole
import com.sibgear.deepseek.settings.ui.external.model.McpServerUiModel
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

    @Test
    fun mcpServersSelectionClosesSettingsAndOpensServersDialog() = runTest {
        val viewModel = SettingsViewModel(coroutineScope = this)

        viewModel.onEvent(SettingsEvent.SettingsDialogOpened)
        viewModel.onEvent(SettingsEvent.McpServersDialogOpened)

        assertEquals(false, viewModel.state.isSettingsDialogOpen)
        assertEquals(true, viewModel.state.isMcpServersDialogOpen)
        assertEquals(emptyList(), viewModel.state.mcpServers)
    }

    @Test
    fun mcpServerDraftChangesAndSaveAddsStreamableHttpServer() = runTest {
        val viewModel = SettingsViewModel(coroutineScope = this)

        viewModel.onEvent(SettingsEvent.McpServersDialogOpened)
        viewModel.onEvent(SettingsEvent.McpServerAddClicked)
        viewModel.onEvent(SettingsEvent.McpServerDraftNameChanged("ai_challenge"))
        viewModel.onEvent(SettingsEvent.McpServerDraftUrlChanged("http://127.0.0.1:3000/mcp"))
        viewModel.onEvent(SettingsEvent.McpServerSaved)

        assertEquals(true, viewModel.state.isMcpServersDialogOpen)
        assertEquals(false, viewModel.state.isMcpServerFormDialogOpen)
        assertEquals(1, viewModel.state.mcpServers.size)
        assertEquals("ai_challenge", viewModel.state.mcpServers.single().name)
        assertEquals("http://127.0.0.1:3000/mcp", viewModel.state.mcpServers.single().url)
        assertEquals(true, viewModel.state.mcpServers.single().isEnabled)
    }

    @Test
    fun mcpServerSaveIgnoresBlankNameOrUrl() = runTest {
        val viewModel = SettingsViewModel(coroutineScope = this)

        viewModel.onEvent(SettingsEvent.McpServerAddClicked)
        viewModel.onEvent(SettingsEvent.McpServerDraftNameChanged("ai_challenge"))
        viewModel.onEvent(SettingsEvent.McpServerSaved)
        assertEquals(emptyList(), viewModel.state.mcpServers)
        assertEquals(true, viewModel.state.isMcpServerFormDialogOpen)

        viewModel.onEvent(SettingsEvent.McpServerDraftNameChanged(""))
        viewModel.onEvent(SettingsEvent.McpServerDraftUrlChanged("http://127.0.0.1:3000/mcp"))
        viewModel.onEvent(SettingsEvent.McpServerSaved)
        assertEquals(emptyList(), viewModel.state.mcpServers)
        assertEquals(true, viewModel.state.isMcpServerFormDialogOpen)
    }

    @Test
    fun mcpServerEditOpensSelectedServerAndSaveUpdatesUrl() = runTest {
        val viewModel = SettingsViewModel(coroutineScope = this)

        viewModel.addMcpServerForTest(
            name = "ai_challenge",
            url = "http://127.0.0.1:3000/mcp",
        )
        val serverId = viewModel.state.mcpServers.single().id

        viewModel.onEvent(SettingsEvent.McpServerEditClicked(serverId))
        assertEquals(false, viewModel.state.isMcpServersDialogOpen)
        assertEquals(true, viewModel.state.isMcpServerFormDialogOpen)
        assertEquals("ai_challenge", viewModel.state.mcpServerDraft.name)

        viewModel.onEvent(SettingsEvent.McpServerDraftUrlChanged("https://mcp.example.com/mcp"))
        viewModel.onEvent(SettingsEvent.McpServerSaved)

        assertEquals(true, viewModel.state.isMcpServersDialogOpen)
        assertEquals(false, viewModel.state.isMcpServerFormDialogOpen)
        assertEquals("https://mcp.example.com/mcp", viewModel.state.mcpServers.single().url)
    }

    @Test
    fun mcpServerUninstallRemovesSelectedServer() = runTest {
        val viewModel = SettingsViewModel(coroutineScope = this)

        viewModel.addMcpServerForTest(
            name = "ai_challenge",
            url = "http://127.0.0.1:3000/mcp",
        )
        val serverId = viewModel.state.mcpServers.single().id

        viewModel.onEvent(SettingsEvent.McpServerEditClicked(serverId))
        viewModel.onEvent(SettingsEvent.McpServerUninstalled)

        assertEquals(true, viewModel.state.isMcpServersDialogOpen)
        assertEquals(false, viewModel.state.isMcpServerFormDialogOpen)
        assertEquals(emptyList(), viewModel.state.mcpServers)
    }

    @Test
    fun mcpServerEnabledToggleChangesOnlyLocalFlag() = runTest {
        val viewModel = SettingsViewModel(coroutineScope = this)

        viewModel.addMcpServerForTest(
            name = "ai_challenge",
            url = "http://127.0.0.1:3000/mcp",
        )
        val serverId = viewModel.state.mcpServers.single().id

        viewModel.onEvent(SettingsEvent.McpServerEnabledChanged(serverId, false))

        assertEquals(false, viewModel.state.mcpServers.single().isEnabled)
        assertEquals("ai_challenge", viewModel.state.mcpServers.single().name)
        assertEquals("http://127.0.0.1:3000/mcp", viewModel.state.mcpServers.single().url)
    }

    @Test
    fun mcpServersRestoreFromInitialListAndNotifyOnChanges() = runTest {
        val savedSnapshots = mutableListOf<List<McpServerUiModel>>()
        val viewModel = SettingsViewModel(
            coroutineScope = this,
            initialMcpServers = listOf(
                McpServerUiModel(
                    id = 7,
                    name = "restored",
                    url = "http://127.0.0.1:3000/mcp",
                    isEnabled = false,
                ),
            ),
            onMcpServersChanged = { savedSnapshots += it },
        )

        assertEquals(7, viewModel.state.mcpServers.single().id)
        assertEquals(false, viewModel.state.mcpServers.single().isEnabled)

        viewModel.onEvent(SettingsEvent.McpServerEnabledChanged(7, true))

        assertEquals(true, viewModel.state.mcpServers.single().isEnabled)
        assertEquals(listOf(viewModel.state.mcpServers), savedSnapshots)
    }

    private fun SettingsViewModel.addMcpServerForTest(
        name: String,
        url: String,
    ) {
        onEvent(SettingsEvent.McpServerAddClicked)
        onEvent(SettingsEvent.McpServerDraftNameChanged(name))
        onEvent(SettingsEvent.McpServerDraftUrlChanged(url))
        onEvent(SettingsEvent.McpServerSaved)
    }
}
