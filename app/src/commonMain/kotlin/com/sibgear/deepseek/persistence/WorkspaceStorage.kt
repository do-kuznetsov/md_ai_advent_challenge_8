package com.sibgear.deepseek.persistence

import com.sibgear.deepseek.chat.workspace.ui.external.model.ChatStorageType
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class WorkspaceStorage(
    val baseDir: File,
) {
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
        selectedStorageType: ChatStorageType,
    ) {
        val safeTabs = tabs
            .filter { it.number > 0 }
            .distinctBy { it.number }
            .ifEmpty { listOf(WorkspaceTabSnapshot(number = 1)) }
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
                selectedStorageType = selectedStorageType,
            ),
        )
    }

    fun jsonHistoryFile(): File =
        File(baseDir, "chat-history.json")

    fun databaseHistoryFile(): File =
        File(baseDir, "chat-history.db")

    fun storageDirectoryLabel(): String =
        "storage: ${baseDir.absolutePath}"

    private fun fallbackSnapshot(): WorkspaceSnapshot =
        WorkspaceSnapshot(
            tabs = listOf(WorkspaceTabSnapshot(number = 1)),
            activeTabNumber = 1,
            nextTabNumber = 2,
            selectedStorageType = ChatStorageType.Json,
        )

    private fun writeWorkspace(snapshot: WorkspaceSnapshot) {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
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
            .filter { it.number > 0 }
            .distinctBy { it.number }
            .map { WorkspaceTabSnapshot(number = it.number) }
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
            selectedStorageType = selectedStorageType.toChatStorageType(),
        )
    }

    private fun WorkspaceSnapshot.toDto(): WorkspaceFileDto =
        WorkspaceFileDto(
            tabs = tabs.map { WorkspaceTabDto(number = it.number) },
            activeTabNumber = activeTabNumber,
            nextTabNumber = nextTabNumber,
            selectedStorageType = selectedStorageType.storageValue,
        )

    companion object {
        fun default(): WorkspaceStorage =
            WorkspaceStorage(baseDir = defaultStorageBaseDir())
    }
}

internal data class WorkspaceSnapshot(
    val tabs: List<WorkspaceTabSnapshot>,
    val activeTabNumber: Int,
    val nextTabNumber: Int,
    val selectedStorageType: ChatStorageType,
)

internal data class WorkspaceTabSnapshot(
    val number: Int,
)

@Serializable
private data class WorkspaceFileDto(
    val version: Int = WorkspaceFileVersion,
    val tabs: List<WorkspaceTabDto> = emptyList(),
    val activeTabNumber: Int = 1,
    val nextTabNumber: Int = 2,
    val selectedStorageType: String = JsonStorageValue,
)

@Serializable
private data class WorkspaceTabDto(
    val number: Int,
    val historyFileName: String? = null,
)

private const val WorkspaceFileVersion = 1
private const val JsonStorageValue = "json"
private const val DatabaseStorageValue = "db"

private val ChatStorageType.storageValue: String
    get() = when (this) {
        ChatStorageType.Json -> JsonStorageValue
        ChatStorageType.Database -> DatabaseStorageValue
    }

private fun String.toChatStorageType(): ChatStorageType =
    when (this) {
        DatabaseStorageValue -> ChatStorageType.Database
        else -> ChatStorageType.Json
    }

internal fun defaultStorageBaseDir(): File {
    val resourcesDir = System.getProperty("compose.application.resources.dir")
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
    val codeSourceDir = runCatching {
        WorkspaceStorage::class.java
            .protectionDomain
            .codeSource
            .location
            .toURI()
            .let(::File)
            .let { location -> if (location.isDirectory) location else location.parentFile }
    }.getOrNull()
    val fallbackDir = File(System.getProperty("user.dir", "."))

    return storageBaseDirNearExecutable(
        runtimeLocation = resourcesDir ?: codeSourceDir ?: fallbackDir,
    )
}

internal fun storageBaseDirNearExecutable(runtimeLocation: File): File =
    File(runtimeLocation.executableDirectory(), StorageDirectoryName)

private fun File.executableDirectory(): File {
    val normalized = absoluteFile
    val appBundle = generateSequence(normalized) { it.parentFile }
        .firstOrNull { it.name.endsWith(".app") }

    return appBundle?.parentFile
        ?: if (normalized.isFile || normalized.extension.equals("jar", ignoreCase = true)) {
            normalized.parentFile
        } else {
            normalized
        }
        ?: File(".").absoluteFile
}

private const val StorageDirectoryName = "ai-clients-data"
