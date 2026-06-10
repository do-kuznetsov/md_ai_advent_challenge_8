package com.sibgear.deepseek.persistence

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class WorkspaceStorage(
    private val baseDir: File,
) {
    private val historiesDir = File(baseDir, "histories")
    private val workspaceFile = File(baseDir, "chats.json")
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        prettyPrint = true
    }

    fun load(): WorkspaceSnapshot {
        if (!workspaceFile.exists()) {
            return fallbackSnapshot()
        }

        return runCatching {
            workspaceFile.readText()
                .let { json.decodeFromString<WorkspaceFileDto>(it) }
                .toSnapshot()
                .takeIf { it.tabs.isNotEmpty() }
                ?: fallbackSnapshot()
        }.getOrElse {
            preserveCorruptWorkspace()
            fallbackSnapshot()
        }
    }

    fun save(
        tabs: List<WorkspaceTabSnapshot>,
        activeTabNumber: Int,
        nextTabNumber: Int,
    ) {
        val safeTabs = tabs
            .filter { it.number > 0 && it.historyFileName.isNotBlank() }
            .distinctBy { it.number }
            .ifEmpty { listOf(defaultTabSnapshot(number = 1)) }
        val safeActiveTabNumber = activeTabNumber
            .takeIf { number -> safeTabs.any { it.number == number } }
            ?: safeTabs.first().number
        val safeNextTabNumber = maxOf(
            nextTabNumber,
            (safeTabs.maxOfOrNull { it.number } ?: 0) + 1,
        )
        writeWorkspace(
            WorkspaceSnapshot(
                tabs = safeTabs,
                activeTabNumber = safeActiveTabNumber,
                nextTabNumber = safeNextTabNumber,
            ),
        )
    }

    fun historyFile(historyFileName: String): File =
        File(historiesDir, historyFileName)

    fun defaultHistoryFileName(tabNumber: Int): String =
        "tab-$tabNumber.json"

    private fun fallbackSnapshot(): WorkspaceSnapshot =
        WorkspaceSnapshot(
            tabs = listOf(defaultTabSnapshot(number = 1)),
            activeTabNumber = 1,
            nextTabNumber = 2,
        )

    private fun defaultTabSnapshot(number: Int): WorkspaceTabSnapshot =
        WorkspaceTabSnapshot(
            number = number,
            historyFileName = defaultHistoryFileName(number),
        )

    private fun writeWorkspace(snapshot: WorkspaceSnapshot) {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        if (!historiesDir.exists()) {
            historiesDir.mkdirs()
        }

        val tempFile = File(baseDir, "${workspaceFile.name}.tmp")
        tempFile.writeText(json.encodeToString(snapshot.toDto()))
        if (workspaceFile.exists() && !workspaceFile.delete()) {
            tempFile.delete()
            error("Cannot replace workspace file: ${workspaceFile.absolutePath}")
        }
        if (!tempFile.renameTo(workspaceFile)) {
            tempFile.copyTo(workspaceFile, overwrite = true)
            tempFile.delete()
        }
    }

    private fun preserveCorruptWorkspace() {
        runCatching {
            workspaceFile.copyTo(File(baseDir, "${workspaceFile.name}.corrupt"), overwrite = true)
        }
    }

    private fun WorkspaceFileDto.toSnapshot(): WorkspaceSnapshot {
        val safeTabs = tabs
            .filter { it.number > 0 && it.historyFileName.isNotBlank() }
            .distinctBy { it.number }
            .map {
                WorkspaceTabSnapshot(
                    number = it.number,
                    historyFileName = it.historyFileName,
                )
            }
        val active = activeTabNumber
            .takeIf { number -> safeTabs.any { it.number == number } }
            ?: safeTabs.firstOrNull()?.number
            ?: 1
        val next = maxOf(
            nextTabNumber,
            (safeTabs.maxOfOrNull { it.number } ?: 0) + 1,
        )

        return WorkspaceSnapshot(
            tabs = safeTabs,
            activeTabNumber = active,
            nextTabNumber = next,
        )
    }

    private fun WorkspaceSnapshot.toDto(): WorkspaceFileDto =
        WorkspaceFileDto(
            tabs = tabs.map {
                WorkspaceTabDto(
                    number = it.number,
                    historyFileName = it.historyFileName,
                )
            },
            activeTabNumber = activeTabNumber,
            nextTabNumber = nextTabNumber,
        )

    companion object {
        fun default(): WorkspaceStorage =
            WorkspaceStorage(
                baseDir = File(System.getProperty("user.home"), ".ai-clients"),
            )
    }
}

internal data class WorkspaceSnapshot(
    val tabs: List<WorkspaceTabSnapshot>,
    val activeTabNumber: Int,
    val nextTabNumber: Int,
)

internal data class WorkspaceTabSnapshot(
    val number: Int,
    val historyFileName: String,
)

@Serializable
private data class WorkspaceFileDto(
    val version: Int = WorkspaceFileVersion,
    val tabs: List<WorkspaceTabDto> = emptyList(),
    val activeTabNumber: Int = 1,
    val nextTabNumber: Int = 2,
)

@Serializable
private data class WorkspaceTabDto(
    val number: Int,
    val historyFileName: String,
)

private const val WorkspaceFileVersion = 1
