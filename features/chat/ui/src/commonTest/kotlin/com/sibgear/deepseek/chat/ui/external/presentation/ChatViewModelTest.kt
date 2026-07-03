package com.sibgear.deepseek.chat.ui.external.presentation

import com.sibgear.deepseek.chat.domain.interactor.ChatInteractor
import com.sibgear.deepseek.chat.domain.model.AgentResponse
import com.sibgear.deepseek.chat.domain.model.AiRequestData
import com.sibgear.deepseek.chat.domain.model.ChatMessage
import com.sibgear.deepseek.chat.domain.model.ChatRole
import com.sibgear.deepseek.chat.domain.repository.AiChatRepository
import com.sibgear.deepseek.chat.domain.repository.RoutingAiRepository
import com.sibgear.deepseek.chat.ui.external.model.ChatEvent
import com.sibgear.rag.domain.interactor.RagQueryInteractor
import com.sibgear.rag.domain.model.ChunkingStrategyType
import com.sibgear.rag.domain.model.RagSearchResult
import com.sibgear.rag.domain.repository.EmbeddingProvider
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
        assertEquals("Что такое KMP?", viewModel.state.messages.first().content)
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
        )
}

private class RecordingChatRepository : AiChatRepository {
    var callCount: Int = 0
        private set
    var lastRequest: AiRequestData? = null
        private set

    override suspend fun sendMessage(request: AiRequestData): AgentResponse {
        callCount += 1
        lastRequest = request
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

    override suspend fun embed(text: String): FloatArray {
        callCount += 1
        error?.let { throw it }
        return floatArrayOf(1f, 0f)
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

    override suspend fun search(
        indexDirectory: String,
        strategy: ChunkingStrategyType,
        queryEmbedding: FloatArray,
    ): List<RagSearchResult> {
        callCount += 1
        lastIndexDirectory = indexDirectory
        lastStrategy = strategy
        return results
    }
}
