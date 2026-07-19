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
import com.sibgear.deepseek.settings.ui.external.model.McpHeaderUiModel
import com.sibgear.deepseek.settings.ui.external.model.McpServerUiModel
import com.sibgear.deepseek.settings.ui.external.model.sanitizedMcpServers
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class WorkspaceStorage(
    val baseDir: File,
) {
    private val workspaceFile = File(baseDir, "chats.json")
    private val mcpServersFile = File(baseDir, "mcp-servers.json")
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

    fun loadMcpServers(): List<McpServerUiModel> {
        if (!mcpServersFile.exists()) {
            return emptyList()
        }

        return runCatching {
            mcpServersFile.readText()
                .let { json.decodeFromString<McpServersFileDto>(it) }
                .servers
                .mapNotNull { it.toDomain() }
                .sanitizedMcpServers()
        }.getOrElse {
            preserveCorruptMcpServers()
            emptyList()
        }
    }

    fun saveMcpServers(servers: List<McpServerUiModel>) {
        writeMcpServers(servers.sanitizedMcpServers())
    }

    fun jsonHistoryFile(): File =
        File(baseDir, "chat-history.json")

    fun databaseHistoryFile(): File =
        File(baseDir, "chat-history.db")

    fun jsonTaskStageHistoryFile(): File =
        File(baseDir, "task-stage-history.json")

    fun databaseTaskStageHistoryFile(): File =
        File(baseDir, "task-stage-history.db")

    fun jsonMemoryFile(): File =
        File(baseDir, "assistant-memory.json")

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

    private fun writeMcpServers(servers: List<McpServerUiModel>) {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }

        val tempFile = File(baseDir, "${mcpServersFile.name}.tmp")
        tempFile.writeText(
            json.encodeToString(
                McpServersFileDto(
                    servers = servers.map { it.toDto() },
                ),
            ),
        )
        if (mcpServersFile.exists() && !mcpServersFile.delete()) {
            tempFile.delete()
            error("Cannot replace MCP servers file: ${mcpServersFile.absolutePath}")
        }
        if (!tempFile.renameTo(mcpServersFile)) {
            tempFile.copyTo(mcpServersFile, overwrite = true)
            tempFile.delete()
        }
    }

    private fun preserveCorruptWorkspace() {
        runCatching {
            workspaceFile.copyTo(File(baseDir, "${workspaceFile.name}.corrupt"), overwrite = true)
        }
    }

    private fun preserveCorruptMcpServers() {
        runCatching {
            mcpServersFile.copyTo(File(baseDir, "${mcpServersFile.name}.corrupt"), overwrite = true)
        }
    }

    private fun WorkspaceFileDto.toSnapshot(): WorkspaceSnapshot {
        val safeTabs = tabs
            .filter { it.number > 0 }
            .distinctBy { it.number }
            .map {
                WorkspaceTabSnapshot(
                    number = it.number,
                    systemPrompt = it.systemPrompt,
                    taskSession = it.taskSession?.toDomain(),
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
            selectedStorageType = selectedStorageType.toChatStorageType(),
        )
    }

    private fun WorkspaceSnapshot.toDto(): WorkspaceFileDto =
        WorkspaceFileDto(
            tabs = tabs.map {
                WorkspaceTabDto(
                    number = it.number,
                    systemPrompt = it.systemPrompt,
                    taskSession = it.taskSession?.toDto(),
                )
            },
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
    val systemPrompt: String = "",
    val taskSession: TaskSessionSnapshot? = null,
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
    val systemPrompt: String = "",
    val taskSession: TaskSessionSnapshotDto? = null,
)

@Serializable
private data class McpServersFileDto(
    val version: Int = McpServersFileVersion,
    val servers: List<McpServerDto> = emptyList(),
)

@Serializable
private data class McpServerDto(
    val id: Int = 0,
    val name: String = "",
    val url: String = "",
    val enabled: Boolean = true,
    val headers: List<McpHeaderDto> = emptyList(),
)

@Serializable
private data class McpHeaderDto(
    val name: String = "",
    val value: String = "",
)

@Serializable
private data class TaskSessionSnapshotDto(
    val isModeEnabled: Boolean = false,
    val context: TaskContextDto? = null,
    val selectedStage: String = TaskState.Planning.title,
    val stages: List<TaskStageSessionDto> = emptyList(),
    val pendingTransition: TaskTransitionProposalDto? = null,
    val pendingRejection: TaskStageRejectionDto? = null,
)

@Serializable
private data class TaskContextDto(
    val task: String,
    val state: String,
    val step: Int,
    val total: Int,
    val plan: List<String> = emptyList(),
    val done: List<String> = emptyList(),
    val current: String,
    val expectedAction: String,
)

@Serializable
private data class TaskStageSessionDto(
    val state: String,
    val chatId: Int,
    val systemPrompt: String,
    val startUserPrompt: String,
    val input: String = "",
    val output: String? = null,
    val resultStatus: String? = null,
    val resultQuestion: String = "",
    val resultReason: String = "",
    val isReached: Boolean = false,
    val isReadyForTransition: Boolean = false,
)

@Serializable
private data class TaskTransitionProposalDto(
    val from: String,
    val to: String,
    val reason: String,
    val inputForTarget: String,
)

@Serializable
private data class TaskStageRejectionDto(
    val stage: String,
    val rejectedOutput: String,
    val context: TaskContextDto,
    val proposedNextStage: String? = null,
    val proposedInputForTarget: String? = null,
    val question: String? = null,
    val reason: String? = null,
)

private const val WorkspaceFileVersion = 1
private const val McpServersFileVersion = 1
private const val JsonStorageValue = "json"
private const val DatabaseStorageValue = "db"

private fun McpServerUiModel.toDto(): McpServerDto =
    McpServerDto(
        id = id,
        name = name,
        url = url,
        enabled = isEnabled,
        headers = headers.map { header ->
            McpHeaderDto(
                name = header.name,
                value = header.value,
            )
        },
    )

private fun McpServerDto.toDomain(): McpServerUiModel? =
    McpServerUiModel(
        id = id,
        name = name,
        url = url,
        isEnabled = enabled,
        headers = headers.map { header ->
            McpHeaderUiModel(
                name = header.name,
                value = header.value,
            )
        },
    ).takeIf { it.id > 0 && it.name.isNotBlank() && it.url.isNotBlank() }

private fun TaskSessionSnapshot.toDto(): TaskSessionSnapshotDto =
    TaskSessionSnapshotDto(
        isModeEnabled = isModeEnabled,
        context = context?.toDto(),
        selectedStage = selectedStage.title,
        stages = stages.map { it.toDto() },
        pendingTransition = pendingTransition?.toDto(),
        pendingRejection = pendingRejection?.toDto(),
    )

private fun TaskSessionSnapshotDto.toDomain(): TaskSessionSnapshot =
    TaskSessionSnapshot(
        isModeEnabled = isModeEnabled,
        context = context?.toDomain(),
        selectedStage = selectedStage.toTaskState(),
        stages = stages.map { it.toDomain() },
        pendingTransition = pendingTransition?.toDomain(),
        pendingRejection = pendingRejection?.toDomain(),
    )

private fun TaskContext.toDto(): TaskContextDto =
    TaskContextDto(
        task = task,
        state = state.title,
        step = step,
        total = total,
        plan = plan,
        done = done,
        current = current,
        expectedAction = expectedAction.name,
    )

private fun TaskContextDto.toDomain(): TaskContext =
    TaskContext(
        task = task,
        state = state.toTaskState(),
        step = step,
        total = total,
        plan = plan,
        done = done,
        current = current,
        expectedAction = expectedAction.toTaskExpectedAction(),
    )

private fun TaskStageSession.toDto(): TaskStageSessionDto =
    TaskStageSessionDto(
        state = state.title,
        chatId = chatId,
        systemPrompt = systemPrompt,
        startUserPrompt = startUserPrompt,
        input = input,
        output = output,
        resultStatus = resultStatus.name,
        resultQuestion = resultQuestion,
        resultReason = resultReason,
        isReached = isReached,
    )

private fun TaskStageSessionDto.toDomain(): TaskStageSession =
    TaskStageSession(
        state = state.toTaskState(),
        chatId = chatId,
        systemPrompt = systemPrompt,
        startUserPrompt = startUserPrompt,
        input = input,
        output = output,
        resultStatus = resultStatus?.toTaskStageResultStatus()
            ?: if (isReadyForTransition) {
                TaskStageResultStatus.Completed
            } else {
                TaskStageResultStatus.InProgress
            },
        resultQuestion = resultQuestion,
        resultReason = resultReason,
        isReached = isReached,
    )

private fun TaskTransitionProposal.toDto(): TaskTransitionProposalDto =
    TaskTransitionProposalDto(
        from = from.title,
        to = to.title,
        reason = reason,
        inputForTarget = inputForTarget,
    )

private fun TaskTransitionProposalDto.toDomain(): TaskTransitionProposal =
    TaskTransitionProposal(
        from = from.toTaskState(),
        to = to.toTaskState(),
        reason = reason,
        inputForTarget = inputForTarget,
    )

private fun TaskStageRejection.toDto(): TaskStageRejectionDto =
    TaskStageRejectionDto(
        stage = stage.title,
        rejectedOutput = rejectedOutput,
        context = context.toDto(),
        proposedNextStage = proposedNextStage?.title,
        proposedInputForTarget = proposedInputForTarget,
        question = question,
        reason = reason,
    )

private fun TaskStageRejectionDto.toDomain(): TaskStageRejection =
    TaskStageRejection(
        stage = stage.toTaskState(),
        rejectedOutput = rejectedOutput,
        context = context.toDomain(),
        proposedNextStage = proposedNextStage?.toTaskState(),
        proposedInputForTarget = proposedInputForTarget,
        question = question,
        reason = reason,
    )

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

private fun String.toTaskState(): TaskState =
    TaskState.entries.firstOrNull { it.title == this } ?: TaskState.Planning

private fun String.toTaskExpectedAction(): TaskExpectedAction =
    TaskExpectedAction.entries.firstOrNull { it.name == this } ?: TaskExpectedAction.UserPrompt

private fun String.toTaskStageResultStatus(): TaskStageResultStatus =
    TaskStageResultStatus.entries.firstOrNull { it.name == this } ?: TaskStageResultStatus.InProgress

internal fun defaultStorageBaseDir(): File {
    return storageBaseDirNearExecutable(
        runtimeLocation = defaultRuntimeLocation(),
    )
}

internal fun defaultClientFilesDir(): File =
    clientFilesDirNearExecutable(defaultRuntimeLocation())

private fun defaultRuntimeLocation(): File {
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

    return resourcesDir ?: codeSourceDir ?: fallbackDir
}

internal fun storageBaseDirNearExecutable(runtimeLocation: File): File =
    File(runtimeLocation.executableDirectory(), StorageDirectoryName)

internal fun clientFilesDirNearExecutable(runtimeLocation: File): File =
    File(runtimeLocation.executableDirectory(), ClientFilesDirectoryName)

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
private const val ClientFilesDirectoryName = "files"
