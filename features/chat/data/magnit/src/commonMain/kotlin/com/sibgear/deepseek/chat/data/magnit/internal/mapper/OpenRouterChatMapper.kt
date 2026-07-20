package com.sibgear.deepseek.chat.data.magnit.internal.mapper

import com.sibgear.deepseek.assistant.memory.domain.model.MemoryItem
import com.sibgear.deepseek.assistant.memory.domain.model.MemoryLayer
import com.sibgear.deepseek.assistant.memory.domain.model.MemoryUpdate
import com.sibgear.deepseek.assistant.memory.domain.model.MemoryUpdateAction
import com.sibgear.deepseek.chat.data.magnit.internal.model.OpenRouterApiChatMessage
import com.sibgear.deepseek.chat.data.magnit.internal.model.OpenRouterChatCompletionRequest
import com.sibgear.deepseek.chat.data.magnit.internal.model.OpenRouterChatTool
import com.sibgear.deepseek.chat.data.magnit.internal.model.OpenRouterChatToolFunction
import com.sibgear.deepseek.chat.data.magnit.internal.model.OpenRouterResponseUsage
import com.sibgear.deepseek.chat.domain.model.AiRequestData
import com.sibgear.deepseek.chat.domain.model.AiToolDefinition
import com.sibgear.deepseek.chat.domain.model.ApiSettings
import com.sibgear.deepseek.chat.domain.model.ChatMessage
import com.sibgear.deepseek.chat.domain.model.ChatMessageAttachment
import com.sibgear.deepseek.chat.domain.model.ChatMessageFooter
import com.sibgear.deepseek.chat.domain.model.ChatMessageKind
import com.sibgear.deepseek.chat.domain.model.ChatMessageMemoryMetadata
import com.sibgear.deepseek.chat.domain.model.ChatMemoryChange
import com.sibgear.deepseek.chat.domain.model.ChatMemoryChangeAction
import com.sibgear.deepseek.chat.domain.model.ChatMemoryItem
import com.sibgear.deepseek.chat.domain.model.ChatMemoryLayer
import com.sibgear.deepseek.chat.domain.model.ChatRole
import com.sibgear.deepseek.chat.domain.model.ChatBranch
import com.sibgear.deepseek.chat.domain.model.ContextMessage
import com.sibgear.deepseek.chat.domain.model.StickyFact
import com.sibgear.deepseek.chat.domain.model.userApiContent
import com.sibgear.deepseek.chat.domain.model.withMcpToolPolicy
import com.sibgear.deepseek.chat.history.domain.model.HistoryBranch
import com.sibgear.deepseek.chat.history.domain.model.HistoryFact
import com.sibgear.deepseek.chat.history.domain.model.HistoryMemoryChange
import com.sibgear.deepseek.chat.history.domain.model.HistoryMemoryChangeAction
import com.sibgear.deepseek.chat.history.domain.model.HistoryMemoryItem
import com.sibgear.deepseek.chat.history.domain.model.HistoryMemoryLayer
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessage
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageAttachment
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageFooter
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageKind
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessageMemoryMetadata
import com.sibgear.deepseek.chat.history.domain.model.HistoryRole

internal fun AiRequestData.toOpenRouterChatCompletionRequest(
    contextMessages: List<ContextMessage>,
    includeSystemPrompt: Boolean = true,
    servicePrompt: String? = null,
    effectiveSystemPrompt: String? = null,
    stream: Boolean = true,
    tools: List<AiToolDefinition> = emptyList(),
    toolWarnings: List<String> = emptyList(),
    extraMessages: List<OpenRouterApiChatMessage> = emptyList(),
): OpenRouterChatCompletionRequest {
    val trimmedSystemPrompt = (effectiveSystemPrompt ?: systemPrompt)
        .withMcpToolPolicy(hasTools = tools.isNotEmpty(), warnings = toolWarnings)
        .trim()
    return OpenRouterChatCompletionRequest(
        model = model.id,
        messages = buildList {
            if (includeSystemPrompt && trimmedSystemPrompt.isNotEmpty()) {
                add(OpenRouterApiChatMessage(role = "system", content = trimmedSystemPrompt))
            }

            contextMessages.forEach { message ->
                add(OpenRouterApiChatMessage(role = message.role.apiRole, content = message.content))
            }

            servicePrompt?.let { prompt ->
                add(OpenRouterApiChatMessage(role = "user", content = prompt))
            }

            addAll(extraMessages)
        },
        stream = stream,
        temperature = apiSettings.openRouterTemperature(),
        maxTokens = apiSettings.openRouterMaxTokens(),
        stop = apiSettings.openRouterStop(),
        tools = tools.takeIf { it.isNotEmpty() }?.map { tool ->
            OpenRouterChatTool(
                type = "function",
                function = OpenRouterChatToolFunction(
                    name = tool.name,
                    description = tool.description,
                    parameters = tool.parameters,
                ),
            )
        },
        toolChoice = "auto".takeIf { tools.isNotEmpty() },
    )
}

internal fun AiRequestData.toOpenRouterAssistantHistoryMessage(
    content: String,
    responseTimeMs: Long,
    usage: OpenRouterResponseUsage? = null,
    retryCount: Int = 0,
    branchId: Int? = null,
    providerLabel: String = "OpenRouter",
    includeUsageCost: Boolean = true,
): HistoryMessage =
    HistoryMessage(
        role = HistoryRole.Assistant,
        content = content,
        branchId = branchId,
        kind = HistoryMessageKind.Regular,
        sourceLabel = "$providerLabel / ${model.displayName}",
        footer = HistoryMessageFooter(
            responseTimeMs = responseTimeMs,
            promptTokens = usage?.promptTokens,
            completionTokens = usage?.completionTokens,
            totalTokens = usage?.displayTotalTokens,
            cost = usage?.cost.takeIf { includeUsageCost },
            retryCount = retryCount,
        ),
    )

internal fun AiRequestData.toOpenRouterCompressionSummaryHistoryMessage(
    content: String,
    responseTimeMs: Long,
    usage: OpenRouterResponseUsage? = null,
    retryCount: Int = 0,
    providerLabel: String = "OpenRouter",
    includeUsageCost: Boolean = true,
): HistoryMessage =
    HistoryMessage(
        role = HistoryRole.Assistant,
        content = content,
        kind = HistoryMessageKind.CompressionSummary,
        sourceLabel = "$providerLabel / ${model.displayName}",
        footer = HistoryMessageFooter(
            responseTimeMs = responseTimeMs,
            promptTokens = usage?.promptTokens,
            completionTokens = usage?.completionTokens,
            totalTokens = usage?.displayTotalTokens,
            cost = usage?.cost.takeIf { includeUsageCost },
            retryCount = retryCount,
        ),
    )

internal fun AiRequestData.toOpenRouterUserHistoryMessage(
    branchId: Int? = null,
    memory: HistoryMessageMemoryMetadata? = null,
): HistoryMessage =
    HistoryMessage(
        role = HistoryRole.User,
        content = prompt,
        branchId = branchId,
        apiContent = userApiContent(),
        attachment = attachment?.let {
            HistoryMessageAttachment(
                fileName = it.fileName,
                sizeBytes = it.sizeBytes,
            )
        },
        memory = memory,
    )

internal fun List<HistoryMessage>.toChatMessages(): List<ChatMessage> =
    map { it.toChatMessage() }

internal fun List<HistoryMessage>.toContextMessages(): List<ContextMessage> =
    map { it.toContextMessage() }

internal fun List<HistoryFact>.toStickyFacts(): List<StickyFact> =
    map { fact ->
        StickyFact(
            key = fact.key,
            value = fact.value,
        )
    }

internal fun List<StickyFact>.toHistoryFacts(): List<HistoryFact> =
    map { fact ->
        HistoryFact(
            key = fact.key,
            value = fact.value,
        )
    }

internal fun List<HistoryBranch>.toChatBranches(): List<ChatBranch> =
    map { branch ->
        ChatBranch(
            id = branch.id,
            parentId = branch.parentId,
            title = branch.title,
            summary = branch.summary,
        )
    }

internal fun List<ChatBranch>.toHistoryBranches(): List<HistoryBranch> =
    map { branch ->
        HistoryBranch(
            id = branch.id,
            parentId = branch.parentId,
            title = branch.title,
            summary = branch.summary,
        )
    }

private fun HistoryMessage.toContextMessage(): ContextMessage =
    ContextMessage(
        role = role.toChatRole(),
        kind = kind.toChatMessageKind(),
        content = apiContent ?: content,
        branchId = branchId,
    )

private fun HistoryMessage.toChatMessage(): ChatMessage =
    ChatMessage(
        role = role.toChatRole(),
        content = content,
        thinkingContent = thinkingContent,
        branchId = branchId,
        kind = kind.toChatMessageKind(),
        apiContent = apiContent,
        attachment = attachment?.toChatMessageAttachment(),
        memory = memory?.toChatMemoryMetadata(),
        sourceLabel = sourceLabel,
        footer = footer?.toChatMessageFooter(),
    )

internal fun List<MemoryItem>.toChatMemoryItems(): List<ChatMemoryItem> =
    map { item ->
        ChatMemoryItem(
            id = item.id,
            layer = item.layer.toChatMemoryLayer(),
            fact = item.fact,
            importance = item.importance,
        )
    }

internal fun List<MemoryUpdate>.toHistoryMemoryChanges(previousItems: List<MemoryItem>): List<HistoryMemoryChange> =
    mapNotNull { update ->
        val previousItem = update.id?.let { id -> previousItems.firstOrNull { it.id == id } }
        val layer = update.layer ?: previousItem?.layer ?: return@mapNotNull null
        if (layer == MemoryLayer.ShortTerm) {
            return@mapNotNull null
        }
        val fact = update.fact?.trim()?.takeIf { it.isNotEmpty() }
            ?: previousItem?.fact
            ?: return@mapNotNull null
        HistoryMemoryChange(
            action = update.action.toHistoryMemoryChangeAction(),
            layer = layer.toHistoryMemoryLayer(),
            fact = fact,
        )
    }

internal fun ChatMessageMemoryMetadata.toHistoryMemoryMetadata(): HistoryMessageMemoryMetadata =
    HistoryMessageMemoryMetadata(
        storedLayers = storedLayers.map { it.toHistoryMemoryLayer() },
        usedLayers = usedLayers.map { it.toHistoryMemoryLayer() },
        changes = changes.map {
            HistoryMemoryChange(
                action = it.action.toHistoryMemoryChangeAction(),
                layer = it.layer.toHistoryMemoryLayer(),
                fact = it.fact,
            )
        },
        injectedItems = injectedItems.map {
            HistoryMemoryItem(
                id = it.id,
                layer = it.layer.toHistoryMemoryLayer(),
                fact = it.fact,
                importance = it.importance,
            )
        },
        error = error,
    )

internal fun List<ChatMemoryLayer>.toHistoryMemoryLayers(): List<HistoryMemoryLayer> =
    map { it.toHistoryMemoryLayer() }

internal fun List<ChatMemoryItem>.toHistoryMemoryItems(): List<HistoryMemoryItem> =
    map {
        HistoryMemoryItem(
            id = it.id,
            layer = it.layer.toHistoryMemoryLayer(),
            fact = it.fact,
            importance = it.importance,
        )
    }

internal fun HistoryMessageMemoryMetadata.toChatMemoryMetadata(): ChatMessageMemoryMetadata =
    ChatMessageMemoryMetadata(
        storedLayers = storedLayers.map { it.toChatMemoryLayer() },
        usedLayers = usedLayers.map { it.toChatMemoryLayer() },
        changes = changes.map {
            ChatMemoryChange(
                action = it.action.toChatMemoryChangeAction(),
                layer = it.layer.toChatMemoryLayer(),
                fact = it.fact,
            )
        },
        injectedItems = injectedItems.map {
            ChatMemoryItem(
                id = it.id,
                layer = it.layer.toChatMemoryLayer(),
                fact = it.fact,
                importance = it.importance,
            )
        },
        error = error,
    )

private fun HistoryRole.toChatRole(): ChatRole =
    when (this) {
        HistoryRole.User -> ChatRole.User
        HistoryRole.Assistant -> ChatRole.Assistant
    }

private fun HistoryMessageKind.toChatMessageKind(): ChatMessageKind =
    when (this) {
        HistoryMessageKind.Regular -> ChatMessageKind.Regular
        HistoryMessageKind.CompressionSummary -> ChatMessageKind.CompressionSummary
        HistoryMessageKind.TaskStateEvent -> ChatMessageKind.TaskStateEvent
        HistoryMessageKind.RagDiagnostic -> ChatMessageKind.RagDiagnostic
    }

private fun HistoryMessageFooter.toChatMessageFooter(): ChatMessageFooter =
    ChatMessageFooter(
        responseTimeMs = responseTimeMs,
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        totalTokens = totalTokens,
        cost = cost,
        retryCount = retryCount,
    )

private fun HistoryMessageAttachment.toChatMessageAttachment(): ChatMessageAttachment =
    ChatMessageAttachment(
        fileName = fileName,
        sizeBytes = sizeBytes,
    )

private fun MemoryLayer.toChatMemoryLayer(): ChatMemoryLayer =
    when (this) {
        MemoryLayer.ShortTerm -> ChatMemoryLayer.ShortTerm
        MemoryLayer.WorkingMemory -> ChatMemoryLayer.WorkingMemory
        MemoryLayer.LongTermMemory -> ChatMemoryLayer.LongTermMemory
    }

private fun MemoryLayer.toHistoryMemoryLayer(): HistoryMemoryLayer =
    when (this) {
        MemoryLayer.ShortTerm -> HistoryMemoryLayer.ShortTerm
        MemoryLayer.WorkingMemory -> HistoryMemoryLayer.WorkingMemory
        MemoryLayer.LongTermMemory -> HistoryMemoryLayer.LongTermMemory
    }

private fun ChatMemoryLayer.toHistoryMemoryLayer(): HistoryMemoryLayer =
    when (this) {
        ChatMemoryLayer.ShortTerm -> HistoryMemoryLayer.ShortTerm
        ChatMemoryLayer.WorkingMemory -> HistoryMemoryLayer.WorkingMemory
        ChatMemoryLayer.LongTermMemory -> HistoryMemoryLayer.LongTermMemory
    }

private fun HistoryMemoryLayer.toChatMemoryLayer(): ChatMemoryLayer =
    when (this) {
        HistoryMemoryLayer.ShortTerm -> ChatMemoryLayer.ShortTerm
        HistoryMemoryLayer.WorkingMemory -> ChatMemoryLayer.WorkingMemory
        HistoryMemoryLayer.LongTermMemory -> ChatMemoryLayer.LongTermMemory
    }

private fun MemoryUpdateAction.toHistoryMemoryChangeAction(): HistoryMemoryChangeAction =
    when (this) {
        MemoryUpdateAction.Add -> HistoryMemoryChangeAction.Add
        MemoryUpdateAction.Update -> HistoryMemoryChangeAction.Update
        MemoryUpdateAction.Delete -> HistoryMemoryChangeAction.Delete
    }

private fun ChatMemoryChangeAction.toHistoryMemoryChangeAction(): HistoryMemoryChangeAction =
    when (this) {
        ChatMemoryChangeAction.Add -> HistoryMemoryChangeAction.Add
        ChatMemoryChangeAction.Update -> HistoryMemoryChangeAction.Update
        ChatMemoryChangeAction.Delete -> HistoryMemoryChangeAction.Delete
    }

private fun HistoryMemoryChangeAction.toChatMemoryChangeAction(): ChatMemoryChangeAction =
    when (this) {
        HistoryMemoryChangeAction.Add -> ChatMemoryChangeAction.Add
        HistoryMemoryChangeAction.Update -> ChatMemoryChangeAction.Update
        HistoryMemoryChangeAction.Delete -> ChatMemoryChangeAction.Delete
    }

private val ChatRole.apiRole: String
    get() = when (this) {
        ChatRole.User -> "user"
        ChatRole.Assistant -> "assistant"
    }

private fun ApiSettings.openRouterTemperature(): Float? {
    if (!isApiControlEnabled) {
        return null
    }

    return temperature.coerceIn(0f, 1f) * 2f
}

private fun ApiSettings.openRouterMaxTokens(): Int? {
    if (!isApiControlEnabled || maxTokens <= 0) {
        return null
    }

    return maxTokens
}

private fun ApiSettings.openRouterStop(): List<String>? {
    if (!isApiControlEnabled) {
        return null
    }

    val trimmedStopWord = stopWord.trim()
    return trimmedStopWord.takeIf { it.isNotEmpty() }?.let { listOf(it) }
}
