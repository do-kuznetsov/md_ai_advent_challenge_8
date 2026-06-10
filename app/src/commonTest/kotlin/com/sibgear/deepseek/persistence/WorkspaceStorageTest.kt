package com.sibgear.deepseek.persistence

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
                tabs = listOf(WorkspaceTabSnapshot(number = 1, historyFileName = "tab-1.json")),
                activeTabNumber = 1,
                nextTabNumber = 2,
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
                WorkspaceTabSnapshot(number = 1, historyFileName = "tab-1.json"),
                WorkspaceTabSnapshot(number = 3, historyFileName = "tab-3.json"),
            ),
            activeTabNumber = 3,
            nextTabNumber = 4,
        )

        assertEquals(
            WorkspaceSnapshot(
                tabs = listOf(
                    WorkspaceTabSnapshot(number = 1, historyFileName = "tab-1.json"),
                    WorkspaceTabSnapshot(number = 3, historyFileName = "tab-3.json"),
                ),
                activeTabNumber = 3,
                nextTabNumber = 4,
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
        assertTrue(File(baseDir, "chats.json.corrupt").exists())
    }

    private fun tempBaseDir(): File =
        Files.createTempDirectory("ai-clients-workspace")
            .toFile()
}
