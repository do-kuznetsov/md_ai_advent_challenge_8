package com.sibgear.deepseek.persistence

import com.sibgear.deepseek.chat.workspace.ui.external.model.ChatStorageType
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
