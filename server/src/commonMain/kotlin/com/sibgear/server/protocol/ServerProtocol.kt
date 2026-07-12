package com.sibgear.server.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(
    val prompt: String,
    val history: List<ChatHistoryItem> = emptyList(),
    val apiSettings: ServerApiSettings = ServerApiSettings(),
    val ragSettings: ServerRagSettings = ServerRagSettings(),
)

@Serializable
data class ChatHistoryItem(
    val role: ChatHistoryRole,
    val content: String,
)

@Serializable
enum class ChatHistoryRole {
    User,
    Assistant,
}

@Serializable
data class ServerApiSettings(
    val temperature: Float = 0.1f,
    val maxTokens: Int = 2500,
    val numCtx: Int = 32768,
    val topP: Float = 0.85f,
    val seed: Int = 42,
    val repeatPenalty: Float = 1.05f,
    val stopWord: String = "",
)

@Serializable
data class ServerRagSettings(
    val isEnabled: Boolean = true,
    val strategy: ServerRagStrategy = ServerRagStrategy.Structure,
    val isQueryRewriteEnabled: Boolean = false,
    val isFilteringEnabled: Boolean = true,
    val isRerankingEnabled: Boolean = true,
    val topKBeforeFilter: Int = 15,
    val topKAfterFilter: Int = 5,
    val similarityThreshold: Float = 0.7f,
)

@Serializable
enum class ServerRagStrategy {
    Fixed,
    Structure,
}

@Serializable
data class ServerPublicConfig(
    val modelId: String,
    val contextSize: Int,
    val ragEnabledByDefault: Boolean = true,
)

@Serializable
sealed interface ChatStreamEvent {
    @Serializable
    @SerialName("context")
    data class Context(
        val usedTokens: Int,
        val maxTokens: Int,
    ) : ChatStreamEvent

    @Serializable
    @SerialName("ragStatus")
    data class RagStatus(
        val message: String,
    ) : ChatStreamEvent

    @Serializable
    @SerialName("thinkingDelta")
    data class ThinkingDelta(
        val text: String,
    ) : ChatStreamEvent

    @Serializable
    @SerialName("contentDelta")
    data class ContentDelta(
        val text: String,
    ) : ChatStreamEvent

    @Serializable
    @SerialName("done")
    data class Done(
        val content: String,
        val thinking: String = "",
        val usedTokens: Int,
        val maxTokens: Int,
    ) : ChatStreamEvent

    @Serializable
    @SerialName("error")
    data class Error(
        val message: String,
    ) : ChatStreamEvent
}

val ServerProtocolJson = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "type"
}
