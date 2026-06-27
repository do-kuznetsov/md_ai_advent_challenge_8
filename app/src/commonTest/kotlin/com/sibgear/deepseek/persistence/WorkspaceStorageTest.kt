package com.sibgear.deepseek.persistence

import com.sibgear.deepseek.chat.domain.model.TaskContext
import com.sibgear.deepseek.chat.domain.model.TaskExpectedAction
import com.sibgear.deepseek.chat.domain.model.TaskSessionSnapshot
import com.sibgear.deepseek.chat.domain.model.TaskStageRejection
import com.sibgear.deepseek.chat.domain.model.TaskStageResultStatus
import com.sibgear.deepseek.chat.domain.model.TaskStageSession
import com.sibgear.deepseek.chat.domain.model.TaskState
import com.sibgear.deepseek.chat.domain.model.TaskTransitionProposal
import com.sibgear.deepseek.chat.workspace.ui.external.model.ChatStorageType
import com.sibgear.deepseek.settings.ui.external.model.McpServerUiModel
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspaceStorageTest {
    @Test
    fun missingStorageReturnsDefaultTab() {
        val storage = WorkspaceStorage(tempBaseDir())

        val snapshot = storage.load()

        assertEquals(
            WorkspaceSnapshot(
                tabs = listOf(WorkspaceTabSnapshot(number = 1)),
                activeTabNumber = 1,
                nextTabNumber = 2,
                selectedStorageType = ChatStorageType.Json,
            ),
            snapshot,
        )
    }

    @Test
    fun savesAndRestoresWorkspace() {
        val baseDir = tempBaseDir()
        val storage = WorkspaceStorage(baseDir)

        storage.save(
            tabs = listOf(
                WorkspaceTabSnapshot(number = 1),
                WorkspaceTabSnapshot(number = 3),
            ),
            activeTabNumber = 3,
            nextTabNumber = 4,
            selectedStorageType = ChatStorageType.Database,
        )

        assertEquals(
            WorkspaceSnapshot(
                tabs = listOf(
                    WorkspaceTabSnapshot(number = 1),
                    WorkspaceTabSnapshot(number = 3),
                ),
                activeTabNumber = 3,
                nextTabNumber = 4,
                selectedStorageType = ChatStorageType.Database,
            ),
            WorkspaceStorage(baseDir).load(),
        )
    }

    @Test
    fun savesAndRestoresTabSystemPrompt() {
        val baseDir = tempBaseDir()
        val storage = WorkspaceStorage(baseDir)

        storage.save(
            tabs = listOf(WorkspaceTabSnapshot(number = 1, systemPrompt = "system")),
            activeTabNumber = 1,
            nextTabNumber = 2,
            selectedStorageType = ChatStorageType.Json,
        )

        assertEquals(
            WorkspaceSnapshot(
                tabs = listOf(WorkspaceTabSnapshot(number = 1, systemPrompt = "system")),
                activeTabNumber = 1,
                nextTabNumber = 2,
                selectedStorageType = ChatStorageType.Json,
            ),
            WorkspaceStorage(baseDir).load(),
        )
    }

    @Test
    fun legacyWorkspaceWithoutStorageTypeRestoresJsonStorage() {
        val baseDir = tempBaseDir()
        baseDir.mkdirs()
        File(baseDir, "chats.json").writeText(
            """
            {
              "version": 1,
              "tabs": [{"number": 2, "historyFileName": "tab-2.json"}],
              "activeTabNumber": 2,
              "nextTabNumber": 3
            }
            """.trimIndent(),
        )

        assertEquals(
            WorkspaceSnapshot(
                tabs = listOf(WorkspaceTabSnapshot(number = 2)),
                activeTabNumber = 2,
                nextTabNumber = 3,
                selectedStorageType = ChatStorageType.Json,
            ),
            WorkspaceStorage(baseDir).load(),
        )
    }

    @Test
    fun savesAndRestoresTaskStateMachineSnapshot() {
        val baseDir = tempBaseDir()
        val storage = WorkspaceStorage(baseDir)
        val taskSession = TaskSessionSnapshot(
            isModeEnabled = true,
            context = TaskContext(
                task = "Implement FSM",
                state = TaskState.Execution,
                step = 2,
                total = 4,
                plan = listOf("Plan"),
                done = listOf("Planning done"),
                current = "execution",
                expectedAction = TaskExpectedAction.UserConfirmation,
            ),
            selectedStage = TaskState.Execution,
            stages = listOf(
                TaskStageSession(
                    state = TaskState.Planning,
                    chatId = 11,
                    systemPrompt = "planning system",
                    startUserPrompt = "planning input",
                    input = "planning input",
                    output = "planning output",
                    resultStatus = TaskStageResultStatus.Completed,
                    isReached = true,
                ),
            ),
            pendingTransition = TaskTransitionProposal(
                from = TaskState.Execution,
                to = TaskState.Validation,
                reason = "execution completed",
                inputForTarget = "validation input",
            ),
            pendingRejection = TaskStageRejection(
                stage = TaskState.Execution,
                rejectedOutput = "execution output",
                context = TaskContext(
                    task = "Implement FSM",
                    state = TaskState.Execution,
                    step = 2,
                    total = 4,
                    plan = listOf("Plan"),
                    done = listOf("Planning done"),
                    current = "execution",
                    expectedAction = TaskExpectedAction.UserPrompt,
                ),
                proposedNextStage = TaskState.Validation,
                proposedInputForTarget = "validation input",
                question = "What should change?",
                reason = "Needs more information",
            ),
        )

        storage.save(
            tabs = listOf(WorkspaceTabSnapshot(number = 1, taskSession = taskSession)),
            activeTabNumber = 1,
            nextTabNumber = 2,
            selectedStorageType = ChatStorageType.Json,
        )

        assertEquals(
            WorkspaceSnapshot(
                tabs = listOf(WorkspaceTabSnapshot(number = 1, taskSession = taskSession)),
                activeTabNumber = 1,
                nextTabNumber = 2,
                selectedStorageType = ChatStorageType.Json,
            ),
            WorkspaceStorage(baseDir).load(),
        )
    }

    @Test
    fun savesAndRestoresMcpServers() {
        val baseDir = tempBaseDir()
        val storage = WorkspaceStorage(baseDir)

        storage.saveMcpServers(
            listOf(
                McpServerUiModel(
                    id = 1,
                    name = "ai_challenge",
                    url = "http://127.0.0.1:3000/mcp",
                    isEnabled = false,
                ),
                McpServerUiModel(
                    id = 2,
                    name = "node_repl",
                    url = "https://mcp.example.com/mcp",
                    isEnabled = true,
                ),
            ),
        )

        assertEquals(
            listOf(
                McpServerUiModel(
                    id = 1,
                    name = "ai_challenge",
                    url = "http://127.0.0.1:3000/mcp",
                    isEnabled = false,
                ),
                McpServerUiModel(
                    id = 2,
                    name = "node_repl",
                    url = "https://mcp.example.com/mcp",
                    isEnabled = true,
                ),
            ),
            WorkspaceStorage(baseDir).loadMcpServers(),
        )
    }

    @Test
    fun corruptMcpServersStorageReturnsEmptyListAndKeepsCorruptCopy() {
        val baseDir = tempBaseDir()
        baseDir.mkdirs()
        File(baseDir, "mcp-servers.json").writeText("{not-json")

        val servers = WorkspaceStorage(baseDir).loadMcpServers()

        assertEquals(emptyList(), servers)
        assertTrue(File(baseDir, "mcp-servers.json.corrupt").exists())
    }

    @Test
    fun corruptStorageReturnsDefaultTabAndKeepsCorruptCopy() {
        val baseDir = tempBaseDir()
        baseDir.mkdirs()
        File(baseDir, "chats.json").writeText("{not-json")

        val snapshot = WorkspaceStorage(baseDir).load()

        assertEquals(1, snapshot.tabs.single().number)
        assertEquals(ChatStorageType.Json, snapshot.selectedStorageType)
        assertTrue(File(baseDir, "chats.json.corrupt").exists())
    }

    @Test
    fun storageBaseDirIsPlacedNearDirectoryRuntimeLocation() {
        val runtimeLocation = File("/opt/AI Clients")

        assertEquals(
            File("/opt/AI Clients/ai-clients-data"),
            storageBaseDirNearExecutable(runtimeLocation),
        )
    }

    @Test
    fun storageBaseDirIsPlacedNearJarRuntimeLocation() {
        val runtimeLocation = File("/opt/AI Clients/app.jar")

        assertEquals(
            File("/opt/AI Clients/ai-clients-data"),
            storageBaseDirNearExecutable(runtimeLocation),
        )
    }

    @Test
    fun clientFilesDirIsPlacedNearJarRuntimeLocation() {
        val runtimeLocation = File("/opt/AI Clients/app.jar")

        assertEquals(
            File("/opt/AI Clients/files"),
            clientFilesDirNearExecutable(runtimeLocation),
        )
    }

    @Test
    fun storageBaseDirIsPlacedNearMacAppBundle() {
        val runtimeLocation = File("/Applications/AI Clients.app/Contents/Resources")

        assertEquals(
            File("/Applications/ai-clients-data"),
            storageBaseDirNearExecutable(runtimeLocation),
        )
    }

    private fun tempBaseDir(): File =
        Files.createTempDirectory("ai-clients-workspace")
            .toFile()
}
