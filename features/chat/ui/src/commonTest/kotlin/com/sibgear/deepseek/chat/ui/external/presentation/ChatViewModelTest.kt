package com.sibgear.deepseek.chat.ui.external.presentation

import com.sibgear.deepseek.chat.domain.interactor.ChatInteractor
import com.sibgear.deepseek.chat.domain.model.AgentResponse
import com.sibgear.deepseek.chat.domain.model.AiModel
import com.sibgear.deepseek.chat.domain.model.AiProvider
import com.sibgear.deepseek.chat.domain.model.AiRequestData
import com.sibgear.deepseek.chat.domain.model.ApiSettings
import com.sibgear.deepseek.chat.domain.model.ChatMessage
import com.sibgear.deepseek.chat.domain.model.ChatMessageKind
import com.sibgear.deepseek.chat.domain.model.ChatRole
import com.sibgear.deepseek.chat.domain.model.PromptAttachment
import com.sibgear.deepseek.chat.domain.model.StreamingChatDelta
import com.sibgear.deepseek.chat.domain.model.StreamingChatDeltaType
import com.sibgear.deepseek.chat.domain.repository.AiModelsRepository
import com.sibgear.deepseek.chat.domain.repository.RoutingAiRepository
import com.sibgear.deepseek.chat.domain.repository.StreamingAiChatRepository
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
    fun apiSettingsUseOptimizedLocalLlmDefaults() {
        val viewModel = chatViewModel()

        assertEquals(0.1f, viewModel.state.apiSettings.temperature)
        assertEquals(2500, viewModel.state.apiSettings.maxTokens)
        assertEquals("2500", viewModel.state.maxTokensInput)
        assertEquals(32768, viewModel.state.apiSettings.numCtx)
        assertEquals("32768", viewModel.state.numCtxInput)
        assertEquals(0.85f, viewModel.state.apiSettings.topP)
        assertEquals("0.85", viewModel.state.topPInput)
        assertEquals(42, viewModel.state.apiSettings.seed)
        assertEquals("42", viewModel.state.seedInput)
        assertEquals(1.05f, viewModel.state.apiSettings.repeatPenalty)
        assertEquals("1.05", viewModel.state.repeatPenaltyInput)
        assertEquals("", viewModel.state.apiSettings.stopWord)
    }

    @Test
    fun ragIsDisabledByDefault() {
        val viewModel = chatViewModel()

        assertFalse(viewModel.state.isRagEnabled)
    }

    @Test
    fun helpWithoutQuestionAddsLocalHintAndDoesNotCallModel() = runTest {
        val chatRepository = RecordingChatRepository()
        val embeddingProvider = RecordingEmbeddingProvider()
        val ragRepository = RecordingRagSearchRepository()
        val viewModel = chatViewModel(
            chatRepository = chatRepository,
            ragQueryInteractor = RagQueryInteractor(embeddingProvider, ragRepository),
        )

        viewModel.onEvent(ChatEvent.PromptChanged("/help"))
        viewModel.sendPrompt()

        assertEquals(0, chatRepository.callCount)
        assertEquals(0, embeddingProvider.callCount)
        assertEquals(0, ragRepository.callCount)
        assertEquals("", viewModel.state.prompt)
        assertEquals(ChatRole.User, viewModel.state.messages.first().role)
        assertEquals("/help", viewModel.state.messages.first().content)
        assertEquals(ChatRole.Assistant, viewModel.state.messages.last().role)
        assertTrue(viewModel.state.messages.last().content.contains("Команда /help"))
    }

    @Test
    fun helpQuestionForcesRagAndSendsQuestionWithoutCommandPrefix() = runTest {
        val chatRepository = RecordingChatRepository()
        val embeddingProvider = RecordingEmbeddingProvider()
        val ragRepository = RecordingRagSearchRepository(
            results = listOf(
                RagSearchResult(
                    source = "README.md",
                    title = "README.md",
                    section = "Структура",
                    chunkId = "readme-structure",
                    text = "Проект состоит из app, rag и mcp модулей.",
                    score = 0.9f,
                ),
            ),
        )
        val viewModel = chatViewModel(
            chatRepository = chatRepository,
            ragQueryInteractor = RagQueryInteractor(embeddingProvider, ragRepository),
            ragRerankerFactory = { RecordingReranker() },
        )

        viewModel.onEvent(ChatEvent.PromptChanged("/help какие модули есть в проекте?"))
        viewModel.sendPrompt()

        val request = requireNotNull(chatRepository.lastRequest)
        assertEquals("какие модули есть в проекте?", request.prompt)
        assertEquals("какие модули есть в проекте?", embeddingProvider.lastText)
        assertEquals(1, ragRepository.callCount)
        assertEquals("../rag/indexed", ragRepository.lastIndexDirectory)
        assertEquals(ChunkingStrategyType.Structure, ragRepository.lastStrategy)
        assertTrue(request.systemPrompt.contains("Ты ассистент разработчика"))
        assertTrue(request.systemPrompt.contains("get_git_branch"))
        assertTrue(request.systemPrompt.contains("[RAG_CONTEXT]"))
        assertTrue(request.systemPrompt.contains("chunk_id: readme-structure"))
        assertEquals("какие модули есть в проекте?", viewModel.state.messages.last { it.role == ChatRole.User }.content)
    }

    @Test
    fun regularPromptDoesNotEnableDeveloperHelpMode() = runTest {
        val chatRepository = RecordingChatRepository()
        val embeddingProvider = RecordingEmbeddingProvider()
        val ragRepository = RecordingRagSearchRepository()
        val viewModel = chatViewModel(
            chatRepository = chatRepository,
            ragQueryInteractor = RagQueryInteractor(embeddingProvider, ragRepository),
        )

        viewModel.onEvent(ChatEvent.PromptChanged("какие модули есть в проекте?"))
        viewModel.sendPrompt()

        val request = requireNotNull(chatRepository.lastRequest)
        assertEquals("какие модули есть в проекте?", request.prompt)
        assertEquals(0, embeddingProvider.callCount)
        assertEquals(0, ragRepository.callCount)
        assertFalse(request.systemPrompt.contains("Ты ассистент разработчика"))
    }

    @Test
    fun initialAttachmentIsSentWithFirstPromptAndThenCleared() = runTest {
        val chatRepository = RecordingChatRepository()
        val attachment = PromptAttachment(
            fileName = "photo_cloud_reviews_200.txt",
            sizeBytes = 12,
            content = "review corpus",
        )
        val viewModel = chatViewModel(
            chatRepository = chatRepository,
            initialAttachment = attachment,
        )

        assertEquals(attachment, viewModel.state.attachment)

        viewModel.onEvent(ChatEvent.PromptChanged("Проанализируй отзывы"))
        viewModel.sendPrompt()

        assertEquals(attachment, chatRepository.lastRequest?.attachment)
        assertEquals(null, viewModel.state.attachment)
    }

    @Test
    fun ollamaApiOptionEventsUpdateSettingsAndInputs() {
        val viewModel = chatViewModel()

        viewModel.onEvent(ChatEvent.NumCtxChanged("16 384"))
        viewModel.onEvent(ChatEvent.TopPChanged("0,9"))
        viewModel.onEvent(ChatEvent.SeedChanged("7"))
        viewModel.onEvent(ChatEvent.RepeatPenaltyChanged("1,10"))

        assertEquals(16384, viewModel.state.apiSettings.numCtx)
        assertEquals("16384", viewModel.state.numCtxInput)
        assertEquals(0.9f, viewModel.state.apiSettings.topP)
        assertEquals("0.9", viewModel.state.topPInput)
        assertEquals(7, viewModel.state.apiSettings.seed)
        assertEquals("7", viewModel.state.seedInput)
        assertEquals(1.10f, viewModel.state.apiSettings.repeatPenalty)
        assertEquals("1.10", viewModel.state.repeatPenaltyInput)
    }

    @Test
    fun loadModelsAddsOllamaModelsToState() = runTest {
        val ollamaModel = AiModel(id = "qwen3:8b", provider = AiProvider.Ollama)
        val viewModel = chatViewModel(
            modelRepositories = mapOf(
                AiProvider.Ollama to FakeModelsRepository(listOf(ollamaModel)),
            ),
        )

        viewModel.loadModels()

        assertEquals(listOf(ollamaModel), viewModel.state.ollamaModels)
        assertEquals("Ollama: 1 моделей", viewModel.state.ollamaModelsStatus)
    }

    @Test
    fun loadModelsSelectsFirstOllamaModelWhenDefaultModelWasSelected() = runTest {
        val firstOllamaModel = AiModel(id = "qwen3:8b", provider = AiProvider.Ollama)
        val secondOllamaModel = AiModel(id = "mistral:7b", provider = AiProvider.Ollama)
        val magnitCopilotModel = AiModel(id = "mcp-chat", provider = AiProvider.MagnitCopilot)
        val viewModel = chatViewModel(
            modelRepositories = mapOf(
                AiProvider.Ollama to FakeModelsRepository(listOf(firstOllamaModel, secondOllamaModel)),
                AiProvider.MagnitCopilot to FakeModelsRepository(listOf(magnitCopilotModel)),
            ),
        )

        viewModel.loadModels()

        assertEquals(firstOllamaModel, viewModel.state.selectedModel)
    }

    @Test
    fun loadModelsSelectsMagnitCopilotWhenNoLocalModelsAreInstalled() = runTest {
        val magnitCopilotModel = AiModel(id = "mcp-chat", provider = AiProvider.MagnitCopilot)
        val viewModel = chatViewModel(
            modelRepositories = mapOf(
                AiProvider.Ollama to FakeModelsRepository(emptyList()),
                AiProvider.MagnitCopilot to FakeModelsRepository(listOf(magnitCopilotModel)),
            ),
        )

        viewModel.loadModels()

        assertEquals(magnitCopilotModel, viewModel.state.selectedModel)
    }

    @Test
    fun loadModelsFallsBackFromMissingOllamaModelToFirstAvailableOllamaModel() = runTest {
        val missingOllamaModel = AiModel(id = "old-local:latest", provider = AiProvider.Ollama)
        val availableOllamaModel = AiModel(id = "qwen3:8b", provider = AiProvider.Ollama)
        val viewModel = chatViewModel(
            modelRepositories = mapOf(
                AiProvider.Ollama to FakeModelsRepository(listOf(availableOllamaModel)),
            ),
        )

        viewModel.onEvent(ChatEvent.ModelSelected(missingOllamaModel))
        viewModel.loadModels()

        assertEquals(availableOllamaModel, viewModel.state.selectedModel)
    }

    @Test
    fun loadModelsFallsBackFromMissingOllamaModelToMagnitCopilotWhenNoLocalModelsRemain() = runTest {
        val missingOllamaModel = AiModel(id = "old-local:latest", provider = AiProvider.Ollama)
        val magnitCopilotModel = AiModel(id = "mcp-chat", provider = AiProvider.MagnitCopilot)
        val viewModel = chatViewModel(
            modelRepositories = mapOf(
                AiProvider.Ollama to FakeModelsRepository(emptyList()),
                AiProvider.MagnitCopilot to FakeModelsRepository(listOf(magnitCopilotModel)),
            ),
        )

        viewModel.onEvent(ChatEvent.ModelSelected(missingOllamaModel))
        viewModel.loadModels()

        assertEquals(magnitCopilotModel, viewModel.state.selectedModel)
    }

    @Test
    fun sendPromptUsesSelectedOllamaModel() = runTest {
        val chatRepository = RecordingChatRepository()
        val viewModel = chatViewModel(chatRepository = chatRepository)
        val ollamaModel = AiModel(id = "qwen3:8b", provider = AiProvider.Ollama)

        viewModel.onEvent(ChatEvent.ModelSelected(ollamaModel))
        viewModel.onEvent(ChatEvent.PromptChanged("Привет"))
        viewModel.sendPrompt()

        assertEquals(ollamaModel, chatRepository.lastRequest?.model)
    }

    @Test
    fun sendPromptStreamsOllamaThinkingAndFinalContent() = runTest {
        val chatRepository = RecordingChatRepository(
            streamingDeltas = listOf(
                StreamingChatDelta(StreamingChatDeltaType.Thinking, "Думаю"),
                StreamingChatDelta(StreamingChatDeltaType.Thinking, " над отзывами."),
                StreamingChatDelta(StreamingChatDeltaType.Content, "Готовый отчёт."),
            ),
        )
        val viewModel = chatViewModel(chatRepository = chatRepository)
        val ollamaModel = AiModel(id = "qwen3:8b", provider = AiProvider.Ollama)

        viewModel.onEvent(ChatEvent.ModelSelected(ollamaModel))
        viewModel.onEvent(ChatEvent.PromptChanged("Проанализируй отзывы"))
        viewModel.sendPrompt()

        assertEquals(1, chatRepository.streamingCallCount)
        assertEquals("Готовый отчёт.", viewModel.state.messages.last().content)
        assertEquals("Думаю над отзывами.", viewModel.state.messages.last().thinkingContent)
    }

    @Test
    fun sendSyntheticPromptKeepsNonStreamingBehaviorForOllama() = runTest {
        val chatRepository = RecordingChatRepository(
            streamingDeltas = listOf(
                StreamingChatDelta(StreamingChatDeltaType.Thinking, "Не должно стримиться."),
            ),
        )
        val viewModel = chatViewModel(chatRepository = chatRepository)
        val ollamaModel = AiModel(id = "qwen3:8b", provider = AiProvider.Ollama)

        viewModel.onEvent(ChatEvent.ModelSelected(ollamaModel))
        viewModel.sendSyntheticPrompt("service prompt")

        assertEquals(0, chatRepository.streamingCallCount)
        assertEquals(1, chatRepository.callCount)
    }

    @Test
    fun syncRequestSettingsCopiesOllamaModelsAndStatus() {
        val source = chatViewModel()
        val target = chatViewModel()
        val ollamaModel = AiModel(id = "qwen3:8b", provider = AiProvider.Ollama)
        val apiSettings = ApiSettings(
            temperature = 0.4f,
            maxTokens = 1500,
            numCtx = 8192,
            topP = 0.9f,
            seed = 99,
            repeatPenalty = 1.1f,
            isApiControlEnabled = true,
        )

        source.onEvent(ChatEvent.ModelSelected(ollamaModel))
        source.loadModels()
        source.syncRequestSettingsFrom(
            source.state.copy(
                ollamaModels = listOf(ollamaModel),
                ollamaModelsStatus = "Ollama: 1 моделей",
                selectedModel = ollamaModel,
                apiSettings = apiSettings,
                maxTokensInput = "1500",
                numCtxInput = "8192",
                topPInput = "0.9",
                seedInput = "99",
                repeatPenaltyInput = "1.1",
            ),
        )
        target.syncRequestSettingsFrom(source.state)

        assertEquals(listOf(ollamaModel), target.state.ollamaModels)
        assertEquals("Ollama: 1 моделей", target.state.ollamaModelsStatus)
        assertEquals(ollamaModel, target.state.selectedModel)
        assertEquals(apiSettings, target.state.apiSettings)
        assertEquals("1500", target.state.maxTokensInput)
        assertEquals("8192", target.state.numCtxInput)
        assertEquals("0.9", target.state.topPInput)
        assertEquals("99", target.state.seedInput)
        assertEquals("1.1", target.state.repeatPenaltyInput)
    }

    @Test
    fun ragDisabledByDefaultDoesNotCallRetrieval() = runTest {
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
        viewModel.onEvent(ChatEvent.RagFilteringEnabledChanged(false))
        viewModel.onEvent(ChatEvent.RagRerankingEnabledChanged(false))
        viewModel.onEvent(ChatEvent.RagStrategySelected(ChunkingStrategyType.Fixed))
        viewModel.onEvent(ChatEvent.RagIndexDirectoryChanged("/tmp/rag"))
        viewModel.onEvent(ChatEvent.PromptChanged("Что такое KMP?"))
        viewModel.sendPrompt()

        val request = requireNotNull(chatRepository.lastRequest)
        assertEquals("Что такое KMP?", request.prompt)
        assertTrue(request.systemPrompt.contains("[RAG_CONTEXT]"))
        assertTrue(request.systemPrompt.contains("Ответ"))
        assertTrue(request.systemPrompt.contains("Источники"))
        assertTrue(request.systemPrompt.contains("Цитаты"))
        assertTrue(request.systemPrompt.contains("Используй только этот контекст"))
        assertTrue(request.systemPrompt.contains("дословные фрагменты из text"))
        assertTrue(request.systemPrompt.contains("source | section | chunk_id"))
        assertTrue(request.systemPrompt.contains("[source=docs/kmp.md section=Intro chunk_id=chunk-1]"))
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
        viewModel.onEvent(ChatEvent.RagRerankingEnabledChanged(false))
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
        viewModel.onEvent(ChatEvent.RagRerankingEnabledChanged(false))
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
    fun ragFollowUpRetrievalUsesTaskMemoryAndKeepsOriginalUserPrompt() = runTest {
        val chatRepository = RecordingChatRepository()
        val embeddingProvider = RecordingEmbeddingProvider()
        val ragRepository = RecordingRagSearchRepository(
            results = listOf(ragResult("chunk-1", "Нужно заменить Android plugins на KMP plugins.", 0.9f)),
        )
        val viewModel = chatViewModel(
            chatRepository = chatRepository,
            ragQueryInteractor = RagQueryInteractor(embeddingProvider, ragRepository),
        )

        viewModel.appendLocalMessage(
            ChatMessage(
                role = ChatRole.User,
                content = "Цель: мигрировать feature-module promocodes-list на KMP",
            ),
        )
        viewModel.appendLocalMessage(
            ChatMessage(
                role = ChatRole.User,
                content = "Термин: logic = presentation:logic",
            ),
        )
        viewModel.onEvent(ChatEvent.RagEnabledChanged(true))
        viewModel.onEvent(ChatEvent.RagRerankingEnabledChanged(false))
        viewModel.onEvent(ChatEvent.PromptChanged("А plugins какие?"))
        viewModel.sendPrompt()

        val queryText = requireNotNull(ragRepository.lastQueryText)
        assertTrue(queryText.contains("question: А plugins какие?"))
        assertTrue(queryText.contains("task_memory:"))
        assertTrue(queryText.contains("goal: мигрировать feature-module promocodes-list на KMP"))
        assertTrue(queryText.contains("logic = presentation:logic"))
        assertEquals(queryText, embeddingProvider.lastText)

        val request = requireNotNull(chatRepository.lastRequest)
        assertEquals("А plugins какие?", request.prompt)
        assertTrue(request.systemPrompt.contains("[TASK_MEMORY]"))
        assertTrue(request.systemPrompt.contains("goal: мигрировать feature-module promocodes-list на KMP"))
        assertTrue(request.systemPrompt.contains("[RAG_CONTEXT]"))
        assertTrue(request.systemPrompt.contains("source | section | chunk_id"))
        assertEquals("А plugins какие?", viewModel.state.messages.last { it.role == ChatRole.User }.content)
    }

    @Test
    fun restoredTaskMemoryIgnoresDiagnosticMessages() = runTest {
        val chatRepository = RecordingChatRepository()
        val embeddingProvider = RecordingEmbeddingProvider()
        val ragRepository = RecordingRagSearchRepository(
            results = listOf(ragResult("chunk-1", "Контекст по миграции.", 0.9f)),
        )
        val viewModel = chatViewModel(
            chatRepository = chatRepository,
            ragQueryInteractor = RagQueryInteractor(embeddingProvider, ragRepository),
            initialMessages = listOf(
                ChatMessage(
                    role = ChatRole.Assistant,
                    kind = ChatMessageKind.RagDiagnostic,
                    content = "Цель: ложная diagnostic цель",
                ),
                ChatMessage(
                    role = ChatRole.User,
                    content = "Цель: мигрировать checkout feature-module на KMP",
                ),
            ),
        )

        viewModel.onEvent(ChatEvent.RagEnabledChanged(true))
        viewModel.onEvent(ChatEvent.RagRerankingEnabledChanged(false))
        viewModel.onEvent(ChatEvent.PromptChanged("Что дальше?"))
        viewModel.sendPrompt()

        val queryText = requireNotNull(ragRepository.lastQueryText)
        assertTrue(queryText.contains("мигрировать checkout feature-module на KMP"))
        assertFalse(queryText.contains("ложная diagnostic цель"))
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
        viewModel.onEvent(ChatEvent.RagRerankingEnabledChanged(false))
        viewModel.onEvent(ChatEvent.PromptChanged("Что такое KMP?"))
        viewModel.sendPrompt()

        assertEquals(0, chatRepository.callCount)
        assertEquals(ChatRole.Assistant, viewModel.state.messages.last().role)
        assertTrue(viewModel.state.messages.last().content.contains("Не знаю"))
        assertTrue(viewModel.state.messages.last().content.contains("Уточните вопрос"))
        assertTrue(viewModel.state.ragStatus.orEmpty().contains("1->0->0 chunks"))
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
        viewModel.onEvent(ChatEvent.RagRerankingEnabledChanged(false))
        viewModel.onEvent(ChatEvent.PromptChanged("Что такое KMP?"))
        viewModel.sendPrompt()

        assertEquals(0, chatRepository.callCount)
        assertEquals(ChatRole.Assistant, viewModel.state.messages.last().role)
        assertTrue(viewModel.state.messages.last().content.contains("Ошибка RAG: Ollama down"))
    }

    private fun chatViewModel(
        chatRepository: RecordingChatRepository = RecordingChatRepository(),
        modelRepositories: Map<AiProvider, AiModelsRepository> = emptyMap(),
        ragQueryInteractor: RagQueryInteractor? = null,
        ragRerankerFactory: ((String) -> RagReranker)? = null,
        initialMessages: List<ChatMessage> = emptyList(),
        initialAttachment: PromptAttachment? = null,
    ): ChatViewModel =
        ChatViewModel(
            interactor = ChatInteractor(
                repository = RoutingAiRepository(
                    chatRepositories = mapOf(
                        AiProvider.DeepSeek to chatRepository,
                        AiProvider.MagnitCopilot to chatRepository,
                        AiProvider.Ollama to chatRepository,
                    ),
                    modelRepositories = modelRepositories,
                ),
                dispatcher = Dispatchers.Unconfined,
            ),
            coroutineScope = CoroutineScope(Dispatchers.Unconfined),
            initialMessages = initialMessages,
            initialAttachment = initialAttachment,
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
    private val streamingDeltas: List<StreamingChatDelta> = emptyList(),
) : StreamingAiChatRepository {
    var callCount: Int = 0
        private set
    var streamingCallCount: Int = 0
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

    override suspend fun sendStreamingMessage(
        request: AiRequestData,
        onDelta: suspend (StreamingChatDelta) -> Unit,
    ): AgentResponse {
        streamingCallCount += 1
        lastRequest = request
        requests += request

        val thinking = StringBuilder()
        val content = StringBuilder()
        streamingDeltas.forEach { delta ->
            onDelta(delta)
            when (delta.type) {
                StreamingChatDeltaType.Thinking -> thinking.append(delta.text)
                StreamingChatDeltaType.Content -> content.append(delta.text)
            }
        }

        return AgentResponse(
            messages = listOf(
                ChatMessage(role = ChatRole.User, content = request.prompt),
                ChatMessage(
                    role = ChatRole.Assistant,
                    content = content.toString().ifBlank { "ok" },
                    thinkingContent = thinking.toString().takeIf { it.isNotBlank() },
                    sourceLabel = "Ollama / ${request.model.displayName}",
                ),
            ),
        )
    }
}

private class FakeModelsRepository(
    private val models: List<AiModel>,
) : AiModelsRepository {
    override suspend fun loadModels(): List<AiModel> = models
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
    var lastQueryText: String? = null
        private set

    override suspend fun search(
        indexDirectory: String,
        strategy: ChunkingStrategyType,
        queryText: String,
        queryEmbedding: FloatArray,
        limit: Int,
    ): List<RagSearchResult> {
        callCount += 1
        lastIndexDirectory = indexDirectory
        lastStrategy = strategy
        lastLimit = limit
        lastQueryText = queryText
        return results.take(limit)
    }
}
