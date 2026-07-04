package com.sibgear.deepseek.chat.ui.external.presentation

import com.sibgear.deepseek.chat.domain.interactor.ChatInteractor
import com.sibgear.deepseek.chat.domain.model.AgentResponse
import com.sibgear.deepseek.chat.domain.model.AiRequestData
import com.sibgear.deepseek.chat.domain.model.ChatMessage
import com.sibgear.deepseek.chat.domain.model.ChatMessageKind
import com.sibgear.deepseek.chat.domain.model.ChatRole
import com.sibgear.deepseek.chat.domain.repository.AiChatRepository
import com.sibgear.deepseek.chat.domain.repository.RoutingAiRepository
import com.sibgear.deepseek.chat.ui.external.model.ChatEvent
import com.sibgear.rag.domain.interactor.RagQueryInteractor
import com.sibgear.rag.domain.model.ChunkingStrategyType
import com.sibgear.rag.domain.model.RagSearchResult
import com.sibgear.rag.domain.repository.EmbeddingProvider
import com.sibgear.rag.domain.repository.RagReranker
import com.sibgear.rag.domain.repository.RagSearchRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatViewModelTest {
    @Test
    fun systemPromptCanChangeBeforeFirstMessage() {
        val viewModel = chatViewModel()

        viewModel.onEvent(ChatEvent.SystemPromptChanged("system"))

        assertEquals("system", viewModel.state.systemPrompt)
    }

    @Test
    fun systemPromptCannotChangeAfterFirstMessage() {
        val viewModel = chatViewModel()

        viewModel.onEvent(ChatEvent.SystemPromptChanged("system"))
        viewModel.appendLocalMessage(ChatMessage(role = ChatRole.User, content = "hello"))
        viewModel.onEvent(ChatEvent.SystemPromptChanged("changed"))

        assertEquals("system", viewModel.state.systemPrompt)
    }

    @Test
    fun ragDisabledDoesNotCallRetrieval() = runTest {
        val chatRepository = RecordingChatRepository()
        val embeddingProvider = RecordingEmbeddingProvider()
        val ragRepository = RecordingRagSearchRepository()
        val viewModel = chatViewModel(
            chatRepository = chatRepository,
            ragQueryInteractor = RagQueryInteractor(embeddingProvider, ragRepository),
        )

        viewModel.onEvent(ChatEvent.PromptChanged("Что такое KMP?"))
        viewModel.sendPrompt()

        assertEquals(0, embeddingProvider.callCount)
        assertEquals(0, ragRepository.callCount)
        assertEquals("Что такое KMP?", chatRepository.lastRequest?.prompt)
        assertFalse(chatRepository.lastRequest?.systemPrompt.orEmpty().contains("[RAG_CONTEXT]"))
    }

    @Test
    fun ragEnabledAddsContextToSystemPromptAndKeepsOriginalUserPrompt() = runTest {
        val chatRepository = RecordingChatRepository()
        val ragRepository = RecordingRagSearchRepository(
            results = listOf(
                RagSearchResult(
                    source = "docs/kmp.md",
                    title = "kmp.md",
                    section = "Intro",
                    chunkId = "chunk-1",
                    text = "KMP позволяет шарить код между платформами.",
                    score = 0.9f,
                ),
            ),
        )
        val viewModel = chatViewModel(
            chatRepository = chatRepository,
            ragQueryInteractor = RagQueryInteractor(RecordingEmbeddingProvider(), ragRepository),
        )

        viewModel.onEvent(ChatEvent.RagEnabledChanged(true))
        viewModel.onEvent(ChatEvent.RagStrategySelected(ChunkingStrategyType.Fixed))
        viewModel.onEvent(ChatEvent.RagIndexDirectoryChanged("/tmp/rag"))
        viewModel.onEvent(ChatEvent.PromptChanged("Что такое KMP?"))
        viewModel.sendPrompt()

        val request = requireNotNull(chatRepository.lastRequest)
        assertEquals("Что такое KMP?", request.prompt)
        assertTrue(request.systemPrompt.contains("[RAG_CONTEXT]"))
        assertTrue(request.systemPrompt.contains("source: docs/kmp.md"))
        assertTrue(request.systemPrompt.contains("chunk_id: chunk-1"))
        assertEquals("/tmp/rag", ragRepository.lastIndexDirectory)
        assertEquals(ChunkingStrategyType.Fixed, ragRepository.lastStrategy)
        assertEquals(5, ragRepository.lastLimit)
        assertEquals("Что такое KMP?", viewModel.state.messages.first().content)
    }

    @Test
    fun ragFilterAddsOnlyResultsAboveThresholdToSystemPrompt() = runTest {
        val chatRepository = RecordingChatRepository()
        val ragRepository = RecordingRagSearchRepository(
            results = listOf(
                ragResult("high", "Высокорелевантный контекст.", 0.8f),
                ragResult("low", "Слабый контекст.", 0.6f),
            ),
        )
        val viewModel = chatViewModel(
            chatRepository = chatRepository,
            ragQueryInteractor = RagQueryInteractor(RecordingEmbeddingProvider(), ragRepository),
        )

        viewModel.onEvent(ChatEvent.RagEnabledChanged(true))
        viewModel.onEvent(ChatEvent.RagFilteringEnabledChanged(true))
        viewModel.onEvent(ChatEvent.PromptChanged("Что такое KMP?"))
        viewModel.sendPrompt()

        val request = requireNotNull(chatRepository.lastRequest)
        assertTrue(request.systemPrompt.contains("chunk_id: high"))
        assertFalse(request.systemPrompt.contains("chunk_id: low"))
        assertEquals(15, ragRepository.lastLimit)
        assertTrue(viewModel.state.ragStatus.orEmpty().contains("2->1->1 chunks"))
        assertTrue(viewModel.state.ragStatus.orEmpty().contains("threshold 0.7"))
        assertTrue(viewModel.state.ragStatus.orEmpty().contains("topK 15/5"))
    }

    @Test
    fun ragRerankAddsRerankScoresToSystemPrompt() = runTest {
        val chatRepository = RecordingChatRepository()
        val ragRepository = RecordingRagSearchRepository(
            results = listOf(
                ragResult("first", "Первый контекст.", 0.9f),
                ragResult("second", "Второй контекст.", 0.8f),
            ),
        )
        val viewModel = chatViewModel(
            chatRepository = chatRepository,
            ragQueryInteractor = RagQueryInteractor(RecordingEmbeddingProvider(), ragRepository),
            ragRerankerFactory = {
                RecordingReranker(
                    scores = mapOf(
                        "first" to 0.1f,
                        "second" to 0.95f,
                    ),
                )
            },
        )

        viewModel.onEvent(ChatEvent.RagEnabledChanged(true))
        viewModel.onEvent(ChatEvent.RagRerankingEnabledChanged(true))
        viewModel.onEvent(ChatEvent.RagRerankerModelDirectoryChanged("/tmp/reranker"))
        viewModel.onEvent(ChatEvent.PromptChanged("Что такое KMP?"))
        viewModel.sendPrompt()

        val systemPrompt = requireNotNull(chatRepository.lastRequest).systemPrompt
        assertTrue(systemPrompt.indexOf("chunk_id: second") < systemPrompt.indexOf("chunk_id: first"))
        assertTrue(systemPrompt.contains("rerank_score: 0.95"))
        assertTrue(systemPrompt.contains("rerank_raw_score: 0.95"))
        assertEquals("/tmp/reranker", lastRerankerModelDirectory)
        assertTrue(viewModel.state.ragStatus.orEmpty().contains("rerank on"))
        assertTrue(viewModel.state.ragStatus.orEmpty().contains("2->2->2 chunks"))
    }

    @Test
    fun ragRerankErrorAddsAssistantMessageAndDoesNotCallFinalLlm() = runTest {
        val chatRepository = RecordingChatRepository()
        val ragRepository = RecordingRagSearchRepository(
            results = listOf(ragResult("first", "Первый контекст.", 0.9f)),
        )
        val viewModel = chatViewModel(
            chatRepository = chatRepository,
            ragQueryInteractor = RagQueryInteractor(RecordingEmbeddingProvider(), ragRepository),
            ragRerankerFactory = {
                RecordingReranker(error = IllegalStateException("model files missing"))
            },
        )

        viewModel.onEvent(ChatEvent.RagEnabledChanged(true))
        viewModel.onEvent(ChatEvent.RagRerankingEnabledChanged(true))
        viewModel.onEvent(ChatEvent.PromptChanged("Что такое KMP?"))
        viewModel.sendPrompt()

        assertEquals(0, chatRepository.callCount)
        assertEquals(ChatRole.Assistant, viewModel.state.messages.last().role)
        assertTrue(viewModel.state.messages.last().content.contains("Ошибка RAG: model files missing"))
    }

    @Test
    fun ragRewriteUsesRewrittenQueryAndShowsDiagnosticMessage() = runTest {
        val chatRepository = RecordingChatRepository(rewriteResponse = "KMP commonMain документация")
        val embeddingProvider = RecordingEmbeddingProvider()
        val ragRepository = RecordingRagSearchRepository(
            results = listOf(ragResult("chunk-1", "KMP позволяет шарить код между платформами.", 0.9f)),
        )
        val viewModel = chatViewModel(
            chatRepository = chatRepository,
            ragQueryInteractor = RagQueryInteractor(embeddingProvider, ragRepository),
        )

        viewModel.onEvent(ChatEvent.RagEnabledChanged(true))
        viewModel.onEvent(ChatEvent.RagQueryRewriteEnabledChanged(true))
        viewModel.onEvent(ChatEvent.PromptChanged("Что такое KMP?"))
        viewModel.sendPrompt()

        assertEquals(2, chatRepository.callCount)
        assertEquals("KMP commonMain документация", embeddingProvider.lastText)
        assertEquals("Что такое KMP?", chatRepository.lastRequest?.prompt)
        assertEquals("Что такое KMP?", viewModel.state.messages.first().content)
        val diagnostic = viewModel.state.messages.single { it.kind == ChatMessageKind.RagDiagnostic }
        assertTrue(diagnostic.content.contains("original: Что такое KMP?"))
        assertTrue(diagnostic.content.contains("rewritten: KMP commonMain документация"))
    }

    @Test
    fun ragFilterEmptyResultAddsAssistantMessageAndDoesNotCallLlm() = runTest {
        val chatRepository = RecordingChatRepository()
        val ragRepository = RecordingRagSearchRepository(
            results = listOf(ragResult("low", "Слабый контекст.", 0.6f)),
        )
        val viewModel = chatViewModel(
            chatRepository = chatRepository,
            ragQueryInteractor = RagQueryInteractor(RecordingEmbeddingProvider(), ragRepository),
        )

        viewModel.onEvent(ChatEvent.RagEnabledChanged(true))
        viewModel.onEvent(ChatEvent.RagFilteringEnabledChanged(true))
        viewModel.onEvent(ChatEvent.PromptChanged("Что такое KMP?"))
        viewModel.sendPrompt()

        assertEquals(0, chatRepository.callCount)
        assertEquals(ChatRole.Assistant, viewModel.state.messages.last().role)
        assertTrue(viewModel.state.messages.last().content.contains("Ошибка RAG"))
    }

    @Test
    fun ragErrorAddsAssistantMessageAndDoesNotCallLlm() = runTest {
        val chatRepository = RecordingChatRepository()
        val viewModel = chatViewModel(
            chatRepository = chatRepository,
            ragQueryInteractor = RagQueryInteractor(
                embeddingProvider = RecordingEmbeddingProvider(error = IllegalStateException("Ollama down")),
                searchRepository = RecordingRagSearchRepository(),
            ),
        )

        viewModel.onEvent(ChatEvent.RagEnabledChanged(true))
        viewModel.onEvent(ChatEvent.PromptChanged("Что такое KMP?"))
        viewModel.sendPrompt()

        assertEquals(0, chatRepository.callCount)
        assertEquals(ChatRole.Assistant, viewModel.state.messages.last().role)
        assertTrue(viewModel.state.messages.last().content.contains("Ошибка RAG: Ollama down"))
    }

    private fun chatViewModel(
        chatRepository: RecordingChatRepository = RecordingChatRepository(),
        ragQueryInteractor: RagQueryInteractor? = null,
        ragRerankerFactory: ((String) -> RagReranker)? = null,
    ): ChatViewModel =
        ChatViewModel(
            interactor = ChatInteractor(
                repository = RoutingAiRepository(
                    chatRepositories = mapOf(
                        com.sibgear.deepseek.chat.domain.model.AiProvider.DeepSeek to chatRepository,
                    ),
                    modelRepositories = emptyMap(),
                ),
                dispatcher = Dispatchers.Unconfined,
            ),
            coroutineScope = CoroutineScope(Dispatchers.Unconfined),
            ragQueryInteractor = ragQueryInteractor,
            ragRerankerFactory = { modelDirectory: String ->
                lastRerankerModelDirectory = modelDirectory
                requireNotNull(ragRerankerFactory).invoke(modelDirectory)
            }.takeIf { ragRerankerFactory != null },
        )

    private var lastRerankerModelDirectory: String? = null

    private fun ragResult(
        id: String,
        text: String,
        score: Float,
    ): RagSearchResult =
        RagSearchResult(
            source = "docs/$id.md",
            title = "$id.md",
            section = "Intro",
            chunkId = id,
            text = text,
            score = score,
        )
}

private class RecordingChatRepository(
    private val rewriteResponse: String = "rewritten query",
) : AiChatRepository {
    var callCount: Int = 0
        private set
    var lastRequest: AiRequestData? = null
        private set
    val requests: MutableList<AiRequestData> = mutableListOf()

    override suspend fun sendMessage(request: AiRequestData): AgentResponse {
        callCount += 1
        lastRequest = request
        requests += request
        if (!request.persistUserMessage) {
            return AgentResponse(
                messages = listOf(
                    ChatMessage(role = ChatRole.Assistant, content = rewriteResponse),
                ),
            )
        }
        return AgentResponse(
            messages = listOf(
                ChatMessage(role = ChatRole.User, content = request.prompt),
                ChatMessage(role = ChatRole.Assistant, content = "ok"),
            ),
        )
    }
}

private class RecordingEmbeddingProvider(
    private val error: Throwable? = null,
) : EmbeddingProvider {
    var callCount: Int = 0
        private set
    var lastText: String? = null
        private set

    override suspend fun embed(text: String): FloatArray {
        callCount += 1
        lastText = text
        error?.let { throw it }
        return floatArrayOf(1f, 0f)
    }
}

private class RecordingReranker(
    private val scores: Map<String, Float> = emptyMap(),
    private val error: Throwable? = null,
) : RagReranker {
    override suspend fun rerank(
        question: String,
        results: List<RagSearchResult>,
    ): List<RagSearchResult> {
        error?.let { throw it }
        return results.map { result ->
            val score = scores[result.chunkId] ?: result.score
            result.copy(
                rerankScore = score,
                rerankRawScore = score,
            )
        }
    }
}

private class RecordingRagSearchRepository(
    private val results: List<RagSearchResult> = emptyList(),
) : RagSearchRepository {
    var callCount: Int = 0
        private set
    var lastIndexDirectory: String? = null
        private set
    var lastStrategy: ChunkingStrategyType? = null
        private set
    var lastLimit: Int? = null
        private set

    override suspend fun search(
        indexDirectory: String,
        strategy: ChunkingStrategyType,
        queryEmbedding: FloatArray,
        limit: Int,
    ): List<RagSearchResult> {
        callCount += 1
        lastIndexDirectory = indexDirectory
        lastStrategy = strategy
        lastLimit = limit
        return results.take(limit)
    }
}
