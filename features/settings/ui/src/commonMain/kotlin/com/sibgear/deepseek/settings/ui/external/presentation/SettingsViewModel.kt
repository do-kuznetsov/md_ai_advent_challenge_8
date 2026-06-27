package com.sibgear.deepseek.settings.ui.external.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sibgear.deepseek.assistant.memory.domain.model.AssistantInvariant
import com.sibgear.deepseek.assistant.memory.domain.model.InvariantCategory
import com.sibgear.deepseek.assistant.memory.domain.model.InvariantCollectionMessage
import com.sibgear.deepseek.assistant.memory.domain.model.InvariantCollectionRole
import com.sibgear.deepseek.settings.ui.external.model.InvariantsChatMessage
import com.sibgear.deepseek.settings.ui.external.model.InvariantsChatRole
import com.sibgear.deepseek.settings.ui.external.model.McpServerDraft
import com.sibgear.deepseek.settings.ui.external.model.McpServerUiModel
import com.sibgear.deepseek.settings.ui.external.model.SettingsEvent
import com.sibgear.deepseek.settings.ui.external.model.SettingsViewState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val coroutineScope: CoroutineScope,
    private val loadProfile: suspend () -> String = { "" },
    private val saveProfile: suspend (text: String) -> String = { text -> text },
    private val updateProfileFromInterview: suspend (
        currentProfile: String,
        answers: List<String>,
    ) -> String = { currentProfile, _ -> currentProfile },
    private val loadInvariants: suspend () -> List<AssistantInvariant> = { emptyList() },
    private val saveInvariants: suspend (
        invariants: List<AssistantInvariant>,
    ) -> List<AssistantInvariant> = { invariants -> invariants },
    private val updateInvariantsFromChat: suspend (
        currentInvariants: List<AssistantInvariant>,
        chatMessages: List<InvariantCollectionMessage>,
    ) -> List<AssistantInvariant> = { currentInvariants, _ -> currentInvariants },
) {
    private var nextMcpServerId = 1

    var state by mutableStateOf(SettingsViewState())
        private set

    fun onEvent(event: SettingsEvent) {
        when (event) {
            SettingsEvent.SettingsDialogOpened -> {
                state = state.copy(isSettingsDialogOpen = true)
            }
            SettingsEvent.SettingsDialogClosed -> {
                state = state.copy(isSettingsDialogOpen = false)
            }
            SettingsEvent.ProfileDialogOpened -> openProfileDialog()
            SettingsEvent.ProfileDialogClosed -> closeProfileDialog()
            is SettingsEvent.ProfileDraftChanged -> {
                state = state.copy(profileDraft = event.text, profileError = null)
            }
            SettingsEvent.ProfileSaved -> saveProfile()
            SettingsEvent.ProfileInterviewStarted -> startProfileInterview()
            is SettingsEvent.ProfileInterviewAnswerChanged -> {
                state = state.copy(profileInterviewAnswerInput = event.text, profileError = null)
            }
            SettingsEvent.ProfileInterviewAnswerSubmitted -> submitProfileInterviewAnswer()
            SettingsEvent.InvariantsDialogOpened -> openInvariantsDialog()
            SettingsEvent.InvariantsDialogClosed -> closeInvariantsDialog()
            is SettingsEvent.InvariantsDraftChanged -> {
                state = state.copy(invariantsDraft = event.text, invariantsError = null)
            }
            SettingsEvent.InvariantsSaved -> saveInvariants()
            is SettingsEvent.InvariantsChatInputChanged -> {
                state = state.copy(invariantsChatInput = event.text, invariantsError = null)
            }
            SettingsEvent.InvariantsChatMessageSent -> sendInvariantsChatMessage()
            SettingsEvent.InvariantsApplied -> applyInvariantsChat()
            SettingsEvent.McpServersDialogOpened -> openMcpServersDialog()
            SettingsEvent.McpServersDialogClosed -> closeMcpServersDialog()
            SettingsEvent.McpServerAddClicked -> openMcpServerAddDialog()
            is SettingsEvent.McpServerEditClicked -> openMcpServerEditDialog(event.id)
            SettingsEvent.McpServerFormClosed -> closeMcpServerFormDialog()
            is SettingsEvent.McpServerDraftNameChanged -> {
                state = state.copy(mcpServerDraft = state.mcpServerDraft.copy(name = event.text))
            }
            is SettingsEvent.McpServerDraftUrlChanged -> {
                state = state.copy(mcpServerDraft = state.mcpServerDraft.copy(url = event.text))
            }
            SettingsEvent.McpServerSaved -> saveMcpServer()
            SettingsEvent.McpServerUninstalled -> uninstallMcpServer()
            is SettingsEvent.McpServerEnabledChanged -> updateMcpServerEnabled(event.id, event.isEnabled)
        }
    }

    private fun openProfileDialog() {
        state = state.copy(
            isSettingsDialogOpen = false,
            isProfileDialogOpen = true,
            profileDraft = EmptyProfileTemplate,
            profileError = null,
            isProfileInterviewActive = false,
            isProfileInterviewLoading = false,
            isProfileSaving = false,
        )
        coroutineScope.launch {
            runCatching { loadProfile() }
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
                    state = state.copy(profileError = formatSettingsError(exception))
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
        val text = state.profileDraft
        state = state.copy(isProfileSaving = true, profileError = null)
        coroutineScope.launch {
            runCatching { saveProfile(text) }
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
                        profileError = formatSettingsError(exception),
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

        val currentProfile = state.profileDraft
        state = state.copy(
            isProfileInterviewLoading = true,
            profileInterviewAnswers = answers,
            profileInterviewAnswerInput = "",
            profileError = null,
        )
        coroutineScope.launch {
            runCatching {
                updateProfileFromInterview(currentProfile, answers)
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
                    profileError = formatSettingsError(exception),
                )
            }
        }
    }

    private fun openInvariantsDialog() {
        state = state.copy(
            isSettingsDialogOpen = false,
            isInvariantsDialogOpen = true,
            invariantsDraft = EmptyInvariantsTemplate,
            invariantsError = null,
            isInvariantsSaving = false,
            isInvariantsApplying = false,
            invariantsChatMessages = state.invariantsChatMessages.ifEmpty { initialInvariantsChatMessages() },
            invariantsChatInput = "",
        )
        coroutineScope.launch {
            runCatching { loadInvariants() }
                .onSuccess { invariants ->
                    state = state.copy(
                        invariantsDraft = invariants.toDraftText().ifBlank { EmptyInvariantsTemplate },
                        invariantsError = null,
                    )
                }
                .onFailure { exception ->
                    if (exception is CancellationException) {
                        throw exception
                    }
                    state = state.copy(invariantsError = formatSettingsError(exception))
                }
        }
    }

    private fun closeInvariantsDialog() {
        state = state.copy(
            isInvariantsDialogOpen = false,
            isInvariantsSaving = false,
            isInvariantsApplying = false,
            invariantsChatMessages = emptyList(),
            invariantsChatInput = "",
            invariantsError = null,
        )
    }

    private fun saveInvariants() {
        if (!state.isInvariantsActionEnabled) {
            return
        }
        val invariants = state.invariantsDraft.toInvariants()
        state = state.copy(isInvariantsSaving = true, invariantsError = null)
        coroutineScope.launch {
            runCatching { saveInvariants(invariants) }
                .onSuccess { savedInvariants ->
                    state = state.copy(
                        invariantsDraft = savedInvariants.toDraftText().ifBlank { EmptyInvariantsTemplate },
                        isInvariantsSaving = false,
                        isInvariantsDialogOpen = false,
                    )
                }
                .onFailure { exception ->
                    if (exception is CancellationException) {
                        throw exception
                    }
                    state = state.copy(
                        isInvariantsSaving = false,
                        invariantsError = formatSettingsError(exception),
                    )
                }
        }
    }

    private fun sendInvariantsChatMessage() {
        if (!state.isInvariantsActionEnabled) {
            return
        }
        val text = state.invariantsChatInput.trim()
        if (text.isEmpty()) {
            return
        }

        state = state.copy(
            invariantsChatMessages = state.invariantsChatMessages.ifEmpty { initialInvariantsChatMessages() } +
                InvariantsChatMessage(role = InvariantsChatRole.User, text = text),
            invariantsChatInput = "",
            invariantsError = null,
        )
    }

    private fun applyInvariantsChat() {
        if (!state.isInvariantsActionEnabled) {
            return
        }
        val currentInvariants = state.invariantsDraft.toInvariants()
        val chatMessages = state.invariantsChatMessages.ifEmpty { initialInvariantsChatMessages() }
        state = state.copy(
            isInvariantsApplying = true,
            invariantsError = null,
        )
        coroutineScope.launch {
            runCatching {
                updateInvariantsFromChat(currentInvariants, chatMessages.toCollectionMessages())
            }.onSuccess { updatedInvariants ->
                state = state.copy(
                    invariantsDraft = updatedInvariants.toDraftText().ifBlank { EmptyInvariantsTemplate },
                    isInvariantsApplying = false,
                )
            }.onFailure { exception ->
                if (exception is CancellationException) {
                    throw exception
                }
                state = state.copy(
                    isInvariantsApplying = false,
                    invariantsError = formatSettingsError(exception),
                )
            }
        }
    }

    private fun openMcpServersDialog() {
        state = state.copy(
            isSettingsDialogOpen = false,
            isMcpServersDialogOpen = true,
            isMcpServerFormDialogOpen = false,
            mcpServerDraft = McpServerDraft(),
        )
    }

    private fun closeMcpServersDialog() {
        state = state.copy(
            isMcpServersDialogOpen = false,
            isMcpServerFormDialogOpen = false,
            mcpServerDraft = McpServerDraft(),
        )
    }

    private fun openMcpServerAddDialog() {
        state = state.copy(
            isMcpServersDialogOpen = false,
            isMcpServerFormDialogOpen = true,
            mcpServerDraft = McpServerDraft(),
        )
    }

    private fun openMcpServerEditDialog(id: Int) {
        val server = state.mcpServers.firstOrNull { it.id == id } ?: return
        state = state.copy(
            isMcpServersDialogOpen = false,
            isMcpServerFormDialogOpen = true,
            mcpServerDraft = McpServerDraft(
                id = server.id,
                name = server.name,
                url = server.url,
            ),
        )
    }

    private fun closeMcpServerFormDialog() {
        state = state.copy(
            isMcpServersDialogOpen = true,
            isMcpServerFormDialogOpen = false,
            mcpServerDraft = McpServerDraft(),
        )
    }

    private fun saveMcpServer() {
        val draft = state.mcpServerDraft
        if (!draft.canSave) {
            return
        }

        if (draft.id == null) {
            state = state.copy(
                mcpServers = state.mcpServers + McpServerUiModel(
                    id = nextMcpServerId++,
                    name = draft.name.trim(),
                    url = draft.url.trim(),
                    isEnabled = true,
                ),
                isMcpServersDialogOpen = true,
                isMcpServerFormDialogOpen = false,
                mcpServerDraft = McpServerDraft(),
            )
        } else {
            state = state.copy(
                mcpServers = state.mcpServers.map { server ->
                    if (server.id == draft.id) {
                        server.copy(
                            name = draft.name.trim(),
                            url = draft.url.trim(),
                        )
                    } else {
                        server
                    }
                },
                isMcpServersDialogOpen = true,
                isMcpServerFormDialogOpen = false,
                mcpServerDraft = McpServerDraft(),
            )
        }
    }

    private fun uninstallMcpServer() {
        val id = state.mcpServerDraft.id ?: return
        state = state.copy(
            mcpServers = state.mcpServers.filterNot { it.id == id },
            isMcpServersDialogOpen = true,
            isMcpServerFormDialogOpen = false,
            mcpServerDraft = McpServerDraft(),
        )
    }

    private fun updateMcpServerEnabled(
        id: Int,
        isEnabled: Boolean,
    ) {
        state = state.copy(
            mcpServers = state.mcpServers.map { server ->
                if (server.id == id) {
                    server.copy(isEnabled = isEnabled)
                } else {
                    server
                }
            },
        )
    }

    private companion object {
        const val ProfileInterviewQuestionsCount = 5
        val EmptyProfileTemplate = """
            Язык и обращение:
            Детальность ответов:
            Стиль общения:
            Роль и проект:
            Технические рамки:
        """.trimIndent()
        val EmptyInvariantsTemplate = """
            # Формат строки:
            # category | enabled | statement | rationale
            # categories: architecture, technical_decision, stack_constraint, business_rule, process, security, other
        """.trimIndent()
    }
}

private fun formatSettingsError(exception: Throwable): String =
    exception.message ?: exception::class.simpleName ?: "unknown"

private fun initialInvariantsChatMessages(): List<InvariantsChatMessage> =
    listOf(
        InvariantsChatMessage(
            role = InvariantsChatRole.Assistant,
            text = InvariantsCollectionQuestions.joinToString(separator = "\n") { question -> "- $question" },
        ),
    )

private fun List<InvariantsChatMessage>.toCollectionMessages(): List<InvariantCollectionMessage> =
    map { message ->
        InvariantCollectionMessage(
            role = when (message.role) {
                InvariantsChatRole.Assistant -> InvariantCollectionRole.Assistant
                InvariantsChatRole.User -> InvariantCollectionRole.User
            },
            text = message.text,
        )
    }

private fun List<AssistantInvariant>.toDraftText(): String =
    joinToString(separator = "\n") { invariant ->
        listOf(
            invariant.category.storageValue,
            invariant.enabled.toString(),
            invariant.statement,
            invariant.rationale,
        ).joinToString(separator = " | ")
    }

private fun String.toInvariants(): List<AssistantInvariant> =
    lineSequence()
        .map { it.trim() }
        .filter { line -> line.isNotEmpty() && !line.startsWith("#") }
        .mapIndexedNotNull { index, line ->
            val parts = line.split("|").map { it.trim() }
            val category = parts.firstOrNull()?.toInvariantCategory()
            val parsed = when {
                category != null && parts.size >= 3 -> ParsedInvariantLine(
                    category = category,
                    enabled = parts[1].toEnabledFlag(),
                    statement = parts[2],
                    rationale = parts.drop(3).joinToString(separator = " | "),
                )
                category != null && parts.size >= 2 -> ParsedInvariantLine(
                    category = category,
                    enabled = true,
                    statement = parts[1],
                    rationale = parts.drop(2).joinToString(separator = " | "),
                )
                else -> ParsedInvariantLine(
                    category = InvariantCategory.Other,
                    enabled = true,
                    statement = line,
                    rationale = "",
                )
            }
            parsed.statement
                .takeIf { it.isNotBlank() }
                ?.let { statement ->
                    AssistantInvariant(
                        id = "invariant-${index + 1}",
                        category = parsed.category,
                        statement = statement,
                        rationale = parsed.rationale,
                        enabled = parsed.enabled,
                    )
                }
        }
        .toList()

private data class ParsedInvariantLine(
    val category: InvariantCategory,
    val enabled: Boolean,
    val statement: String,
    val rationale: String,
)

private fun String.toEnabledFlag(): Boolean =
    when (lowercase()) {
        "false", "0", "disabled", "off", "no", "нет", "выключен", "выключено" -> false
        else -> true
    }

private val InvariantCategory.storageValue: String
    get() = when (this) {
        InvariantCategory.Architecture -> "architecture"
        InvariantCategory.TechnicalDecision -> "technical_decision"
        InvariantCategory.StackConstraint -> "stack_constraint"
        InvariantCategory.BusinessRule -> "business_rule"
        InvariantCategory.Process -> "process"
        InvariantCategory.Security -> "security"
        InvariantCategory.Other -> "other"
    }

private fun String.toInvariantCategory(): InvariantCategory? =
    when (this) {
        "architecture" -> InvariantCategory.Architecture
        "technical_decision" -> InvariantCategory.TechnicalDecision
        "stack_constraint" -> InvariantCategory.StackConstraint
        "business_rule" -> InvariantCategory.BusinessRule
        "process" -> InvariantCategory.Process
        "security" -> InvariantCategory.Security
        "other" -> InvariantCategory.Other
        else -> null
    }

private val InvariantsCollectionQuestions = listOf(
    "Какая архитектура обязательна для этого проекта и какие архитектурные подходы нельзя предлагать?",
    "Какие технические решения уже приняты и должны сохраняться?",
    "Какие ограничения стека, платформ, зависимостей, кодстайла, тестов и процесса обязательны?",
    "Какие бизнес-правила, безопасность и продуктовые ограничения нельзя нарушать?",
    "Какие решения ассистенту прямо запрещено предлагать даже как альтернативу?",
)
