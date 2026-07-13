package com.sibgear.server

import com.sibgear.server.protocol.ChatRequest
import com.sibgear.server.protocol.ChatStreamEvent
import com.sibgear.server.protocol.ServerApiSettings
import kotlinx.coroutines.CancellationException

interface ChatStreamer {
    suspend fun stream(
        request: ChatRequest,
        emit: suspend (ChatStreamEvent) -> Unit,
    )
}

class ChatService(
    private val config: ServerConfig,
    private val llamaClient: LlamaCppClient = LlamaCppClient(
        baseUrl = config.llamaBaseUrl,
        modelId = config.llamaModelId,
    ),
    private val ragService: RagChatService = RagChatService(config),
) : ChatStreamer {
    override suspend fun stream(
        request: ChatRequest,
        emit: suspend (ChatStreamEvent) -> Unit,
    ) {
        val prompt = request.prompt.trim()
        if (prompt.isEmpty()) {
            emit(ChatStreamEvent.Error("Prompt is empty."))
            return
        }

        val settings = request.apiSettings.normalized(config.llamaContextSize)
        if (settings.numCtx > config.llamaContextSize) {
            emit(ChatStreamEvent.Error("num_ctx=${settings.numCtx} exceeds server context ${config.llamaContextSize}."))
            return
        }

        try {
            val ragContext = ragService.findContext(
                prompt = prompt,
                settings = request.ragSettings,
                rewrite = { query -> rewriteRagQuery(query, settings) },
            )
            ragContext?.status?.let { emit(ChatStreamEvent.RagStatus(it)) }

            val messages = request.toLlamaMessages(
                prompt = prompt,
                ragContext = ragContext,
            )
            val contextText = messages.joinToString("\n") { "${it.role}: ${it.content}" }
            val usedTokens = llamaClient.countTokens(contextText)
            emit(
                ChatStreamEvent.Context(
                    usedTokens = usedTokens,
                    maxTokens = settings.numCtx,
                ),
            )
            if (usedTokens >= settings.numCtx) {
                emit(ChatStreamEvent.Error("Context is full: $usedTokens/${settings.numCtx} tokens."))
                return
            }

            val completion = llamaClient.streamChat(messages, settings) { event ->
                emit(event)
            }
            val finalTokens = llamaClient.countTokens("$contextText\nassistant: ${completion.content}")
            emit(
                ChatStreamEvent.Done(
                    content = completion.content,
                    thinking = completion.thinking,
                    usedTokens = finalTokens,
                    maxTokens = settings.numCtx,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            emit(ChatStreamEvent.Error(error.message ?: error::class.simpleName ?: "unknown error"))
        }
    }

    private suspend fun rewriteRagQuery(
        query: String,
        settings: ServerApiSettings,
    ): String =
        llamaClient.completeOnce(
            messages = listOf(
                LlamaChatMessage(
                    role = "system",
                    content = "Rewrite the user question for semantic document search. Return only one concise query.",
                ),
                LlamaChatMessage(role = "user", content = query),
            ),
            settings = settings.copy(
                temperature = 0f,
                maxTokens = 128,
                stopWord = "",
            ),
        ).lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.removeSurrounding("\"")
            .orEmpty()

    private fun ChatRequest.toLlamaMessages(
        prompt: String,
        ragContext: RagContext?,
    ): List<LlamaChatMessage> =
        buildList {
            if (ragContext != null && ragContext.result.results.isNotEmpty()) {
                add(
                    LlamaChatMessage(
                        role = "system",
                        content = buildString {
                            appendLine("Use RAG context below when it is relevant. If context is insufficient, say so.")
                            appendLine()
                            appendLine(ragContext.promptBlock)
                        },
                    ),
                )
            }
            history.forEach { item ->
                add(LlamaChatMessage(role = item.role.toLlamaRole(), content = item.content))
            }
            add(LlamaChatMessage(role = "user", content = prompt))
        }
}

private fun ServerApiSettings.normalized(serverContextSize: Int): ServerApiSettings =
    copy(
        temperature = temperature.coerceIn(0f, 1f),
        maxTokens = maxTokens.takeIf { it > 0 } ?: 2500,
        numCtx = numCtx.takeIf { it > 0 }?.coerceAtMost(serverContextSize) ?: serverContextSize,
        topP = topP.coerceIn(0f, 1f),
        repeatPenalty = repeatPenalty.coerceAtLeast(0f),
    )
