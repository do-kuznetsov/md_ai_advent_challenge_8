package com.sibgear.deepseek.chat.ui.external.model

import com.sibgear.deepseek.chat.domain.model.ApiSettings
import com.sibgear.deepseek.chat.domain.model.ChatMessage
import com.sibgear.deepseek.chat.domain.model.AiModel
import com.sibgear.deepseek.chat.domain.model.ChatBranch
import com.sibgear.deepseek.chat.domain.model.ContextManagementMode
import com.sibgear.deepseek.chat.domain.model.DefaultContextManagementMessages
import com.sibgear.deepseek.chat.domain.model.PromptAttachment
import com.sibgear.deepseek.chat.domain.model.StickyFact
import com.sibgear.deepseek.chat.ui.internal.mapper.buildContextUsageLabel
import com.sibgear.deepseek.chat.ui.internal.mapper.buildPinnedContextMessageIndex
import com.sibgear.deepseek.chat.ui.internal.model.ChatDefaults
import com.sibgear.rag.domain.model.ChunkingStrategyType

data class ChatViewState(
    val systemPrompt: String = "",
    val isSystemPromptReadOnly: Boolean = false,
    val prompt: String = "",
    val attachment: PromptAttachment? = null,
    val attachmentError: String? = null,
    val selectedModel: AiModel = ChatDefaults.DefaultModel,
    val openRouterModels: List<AiModel> = emptyList(),
    val magnitCopilotModels: List<AiModel> = emptyList(),
    val deepSeekModels: List<AiModel> = listOf(ChatDefaults.DefaultModel),
    val modelFilter: String = "free",
    val openRouterModelsStatus: String? = null,
    val magnitCopilotModelsStatus: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val contextUsageLabel: String = buildContextUsageLabel(messages, selectedModel),
    val isLoading: Boolean = false,
    val isModelMenuExpanded: Boolean = false,
    val contextManagementMode: ContextManagementMode = ContextManagementMode.None,
    val isContextManagementPanelExpanded: Boolean = false,
    val summaryIntervalInput: String = DefaultContextManagementMessages.toString(),
    val slidingWindowMessagesInput: String = DefaultContextManagementMessages.toString(),
    val stickyFactsWindowInput: String = DefaultContextManagementMessages.toString(),
    val stickyFacts: List<StickyFact> = emptyList(),
    val stickyFactsStatus: String? = null,
    val branches: List<ChatBranch> = emptyList(),
    val activeBranchId: Int? = null,
    val branchingStatus: String? = null,
    val pinnedContextMessageIndex: Int? = buildPinnedContextMessageIndex(
        messages = messages,
        mode = contextManagementMode,
        slidingWindowMessagesInput = slidingWindowMessagesInput,
    ),
    val expandedCompressionMessageIndexes: Set<Int> = emptySet(),
    val apiSettings: ApiSettings = ApiSettings(),
    val maxTokensInput: String = ApiSettings().maxTokens.toString(),
    val isRagEnabled: Boolean = false,
    val ragStrategy: ChunkingStrategyType = ChunkingStrategyType.Structure,
    val ragIndexDirectory: String = "../rag/indexed",
    val isRagFilteringEnabled: Boolean = true,
    val ragTopKBeforeFilterInput: String = "15",
    val ragTopKAfterFilterInput: String = "5",
    val ragSimilarityThresholdInput: String = "0.7",
    val isRagQueryRewriteEnabled: Boolean = false,
    val isRagRerankingEnabled: Boolean = true,
    val ragRerankerModelDirectory: String = "../rag/models/bge-reranker-v2-m3",
    val ragStatus: String? = null,
) {
    val availableModels: List<AiModel>
        get() = openRouterModels + magnitCopilotModels + deepSeekModels

    val isSendEnabled: Boolean
        get() = prompt.isNotBlank() && !isLoading
}
