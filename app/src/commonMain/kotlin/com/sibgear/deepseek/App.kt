package com.sibgear.deepseek

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.sibgear.deepseek.assistant.memory.data.jsonfile.external.repository.JsonFileAssistantMemoryRepository
import com.sibgear.deepseek.assistant.memory.data.sqldelight.external.repository.SqldelightAssistantMemoryRepository
import com.sibgear.deepseek.assistant.memory.domain.interactor.AssistantMemoryInteractor
import com.sibgear.deepseek.assistant.memory.domain.model.UserProfile
import com.sibgear.deepseek.assistant.memory.domain.repository.AssistantMemoryRepository
import com.sibgear.deepseek.assistant.memory.domain.service.AssistantInvariantService
import com.sibgear.deepseek.assistant.memory.domain.service.AssistantProfileService
import com.sibgear.deepseek.chat.data.deepseek.external.repository.DeepSeekChatRepository
import com.sibgear.deepseek.chat.data.deepseek.external.repository.DeepSeekModelsRepository
import com.sibgear.deepseek.chat.data.deepseek.external.service.DeepSeekAssistantProfileService
import com.sibgear.deepseek.chat.data.magnit.external.repository.MagnitCopilotChatRepository
import com.sibgear.deepseek.chat.data.magnit.external.repository.MagnitCopilotModelsRepository
import com.sibgear.deepseek.chat.data.magnit.external.service.MagnitCopilotAssistantProfileService
import com.sibgear.deepseek.chat.data.ollama.external.repository.OllamaChatRepository
import com.sibgear.deepseek.chat.data.ollama.external.repository.OllamaModelsRepository
import com.sibgear.deepseek.chat.data.openrouter.external.repository.OpenRouterChatRepository
import com.sibgear.deepseek.chat.data.openrouter.external.repository.OpenRouterModelsRepository
import com.sibgear.deepseek.chat.data.openrouter.external.service.OpenRouterAssistantProfileService
import com.sibgear.deepseek.chat.domain.interactor.ChatInteractor
import com.sibgear.deepseek.chat.domain.model.CompositeAiToolProvider
import com.sibgear.deepseek.chat.domain.model.AiProvider
import com.sibgear.deepseek.chat.domain.model.TaskSessionSnapshot
import com.sibgear.deepseek.chat.domain.model.TaskState
import com.sibgear.deepseek.chat.domain.repository.RoutingAiRepository
import com.sibgear.deepseek.chat.history.data.external.storage.JsonFileChatHistoryStorage
import com.sibgear.deepseek.chat.history.data.sqldelight.external.storage.SqldelightChatHistoryStorage
import com.sibgear.deepseek.chat.history.domain.interactor.ChatHistoryInteractor
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessage
import com.sibgear.deepseek.chat.history.domain.model.HistoryRole
import com.sibgear.deepseek.chat.history.domain.repository.ChatHistoryRepository
import com.sibgear.deepseek.chat.ui.external.presentation.ChatViewModel
import com.sibgear.deepseek.chat.workspace.ui.external.model.ChatStorageType
import com.sibgear.deepseek.chat.workspace.ui.external.model.ChatTab
import com.sibgear.deepseek.chat.workspace.ui.external.model.StorageSwitchResult
import com.sibgear.deepseek.chat.workspace.ui.external.model.TaskModeSession
import com.sibgear.deepseek.chat.workspace.ui.external.model.TaskStageAgent
import com.sibgear.deepseek.chat.workspace.ui.external.model.defaultTaskChatFocus
import com.sibgear.deepseek.chat.workspace.ui.external.presentation.AiChatAppViewModel
import com.sibgear.deepseek.chat.workspace.ui.external.view.AiChatAppScreen
import com.sibgear.deepseek.config.BuildConfig
import com.sibgear.deepseek.mapper.toChatMessages
import com.sibgear.deepseek.mapper.toChatBranches
import com.sibgear.deepseek.mapper.toHistoryBranches
import com.sibgear.deepseek.mapper.toHistoryFacts
import com.sibgear.deepseek.mapper.toHistoryMessages
import com.sibgear.deepseek.mapper.toStickyFacts
import com.sibgear.deepseek.persistence.WorkspaceStorage
import com.sibgear.deepseek.persistence.WorkspaceTabSnapshot
import com.sibgear.deepseek.persistence.defaultClientFilesDir
import com.sibgear.deepseek.settings.ui.external.model.SettingsEvent
import com.sibgear.deepseek.settings.ui.external.presentation.SettingsViewModel
import com.sibgear.deepseek.settings.ui.external.view.SettingsDialogs
import com.sibgear.deepseek.tools.LocalFileAiToolProvider
import com.sibgear.deepseek.tools.LocalTimeAiToolProvider
import com.sibgear.mcp.client.McpAiToolProvider
import com.sibgear.mcp.client.McpServerConnection
import com.sibgear.rag.data.embedding.OllamaEmbeddingProvider
import com.sibgear.rag.data.rerank.OnnxBgeReranker
import com.sibgear.rag.data.sqlite.SQLiteRagSearchRepository
import com.sibgear.rag.domain.interactor.RagQueryInteractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    val workspaceStorage = remember { WorkspaceStorage.default() }
    val initialWorkspace = remember(workspaceStorage) { workspaceStorage.load() }
    val initialStorageType = initialWorkspace.selectedStorageType
    val initialHistoryStorage = remember(workspaceStorage, initialStorageType) {
        workspaceStorage.createHistoryStorage(initialStorageType)
    }
    val initialTabNumbers = remember(initialWorkspace, initialHistoryStorage) {
        mergeTabNumbers(
            currentTabNumbers = initialWorkspace.tabs.map { it.number },
            savedTabNumbers = initialHistoryStorage.loadSavedChatIds(),
        )
    }
    val initialHistoryByTab = remember(initialTabNumbers, initialHistoryStorage) {
        initialTabNumbers.associateWith { tabNumber ->
            runBlocking {
                initialHistoryStorage.createRepository(tabNumber).getMessages()
            }
        }
    }
    val initialTitlesByTab = remember(initialHistoryByTab) {
        initialHistoryByTab.mapValues { (_, messages) -> messages.toTabTitle() }
    }
    val deepSeekAssistantService = remember {
        DeepSeekAssistantProfileService(apiKey = BuildConfig.DEEPSEEK_API_KEY)
    }
    val openRouterAssistantService = remember {
        OpenRouterAssistantProfileService(apiKey = BuildConfig.OPENROUTER_AI_KEY)
    }
    val magnitCopilotAssistantService = remember {
        MagnitCopilotAssistantProfileService(apiKey = BuildConfig.MAGNIT_COPILOT_API_KEY)
    }
    val profileServices = remember(deepSeekAssistantService, magnitCopilotAssistantService, openRouterAssistantService) {
        mapOf<String, AssistantProfileService>(
            AiProvider.DeepSeek.name to deepSeekAssistantService,
            AiProvider.MagnitCopilot.name to magnitCopilotAssistantService,
            AiProvider.OpenRouter.name to openRouterAssistantService,
        )
    }
    val invariantServices = remember(deepSeekAssistantService, magnitCopilotAssistantService, openRouterAssistantService) {
        mapOf<String, AssistantInvariantService>(
            AiProvider.DeepSeek.name to deepSeekAssistantService,
            AiProvider.MagnitCopilot.name to magnitCopilotAssistantService,
            AiProvider.OpenRouter.name to openRouterAssistantService,
        )
    }
    val mcpToolProvider = remember(workspaceStorage) {
        McpAiToolProvider(
            loadServers = {
                workspaceStorage.loadMcpServers().map { server ->
                    McpServerConnection(
                        id = server.id,
                        name = server.name,
                        url = server.url,
                        isEnabled = server.isEnabled,
                    )
                }
            },
        )
    }
    val localFileToolProvider = remember {
        LocalFileAiToolProvider(filesDir = defaultClientFilesDir())
    }
    val localTimeToolProvider = remember {
        LocalTimeAiToolProvider()
    }
    val toolProvider = remember(localFileToolProvider, localTimeToolProvider, mcpToolProvider) {
        CompositeAiToolProvider(
            listOf(
                localFileToolProvider,
                localTimeToolProvider,
                mcpToolProvider,
            ),
        )
    }
    val ragQueryInteractor = remember {
        RagQueryInteractor(
            embeddingProvider = OllamaEmbeddingProvider(model = "nomic-embed-text"),
            searchRepository = SQLiteRagSearchRepository(),
        )
    }
    val ragRerankerFactory = remember {
        val rerankers = mutableMapOf<String, OnnxBgeReranker>()
        val factory: (String) -> OnnxBgeReranker = { modelDirectory ->
            rerankers.getOrPut(modelDirectory) {
                OnnxBgeReranker(modelDirectory = modelDirectory)
            }
        }
        factory
    }

    val viewModel = remember(scope, workspaceStorage, toolProvider, ragQueryInteractor, ragRerankerFactory) {
        fun createChatViewModel(
            tabNumber: Int,
            storageType: ChatStorageType,
            initialMessages: List<HistoryMessage>? = null,
            useTaskStageHistory: Boolean = false,
            initialSystemPrompt: String = "",
            initialPrompt: String = "",
            isSystemPromptReadOnly: Boolean = false,
        ): ChatViewModel {
            val historyStorage = if (useTaskStageHistory) {
                workspaceStorage.createTaskStageHistoryStorage(storageType)
            } else {
                workspaceStorage.createHistoryStorage(storageType)
            }
            val historyRepository = historyStorage.createRepository(tabNumber)
            val historyInteractor = ChatHistoryInteractor(
                repository = historyRepository,
                dispatcher = Dispatchers.Default,
            )
            val memoryInteractor = AssistantMemoryInteractor(
                repository = workspaceStorage.createMemoryRepository(storageType),
                dispatcher = Dispatchers.Default,
            )
            val restoredMessages = initialMessages ?: runBlocking {
                historyInteractor.getMessages()
            }
            val restoredFacts = runBlocking {
                historyInteractor.getFacts()
            }
            val restoredBranches = runBlocking {
                historyInteractor.getBranches()
            }
            val repository = RoutingAiRepository(
                chatRepositories = mapOf(
                    AiProvider.DeepSeek to DeepSeekChatRepository(
                        apiKey = BuildConfig.DEEPSEEK_API_KEY,
                        historyInteractor = historyInteractor,
                        memoryInteractor = memoryInteractor,
                    ),
                    AiProvider.MagnitCopilot to MagnitCopilotChatRepository(
                        apiKey = BuildConfig.MAGNIT_COPILOT_API_KEY,
                        historyInteractor = historyInteractor,
                        memoryInteractor = memoryInteractor,
                    ),
                    AiProvider.OpenRouter to OpenRouterChatRepository(
                        apiKey = BuildConfig.OPENROUTER_AI_KEY,
                        historyInteractor = historyInteractor,
                        memoryInteractor = memoryInteractor,
                    ),
                    AiProvider.Ollama to OllamaChatRepository(
                        historyInteractor = historyInteractor,
                        memoryInteractor = memoryInteractor,
                    ),
                ),
                modelRepositories = mapOf(
                    AiProvider.DeepSeek to DeepSeekModelsRepository(),
                    AiProvider.MagnitCopilot to MagnitCopilotModelsRepository(),
                    AiProvider.OpenRouter to OpenRouterModelsRepository(
                        apiKey = BuildConfig.OPENROUTER_AI_KEY,
                    ),
                    AiProvider.Ollama to OllamaModelsRepository(),
                ),
            )
            val interactor = ChatInteractor(
                repository = repository,
                dispatcher = Dispatchers.IO,
            )

            return ChatViewModel(
                interactor = interactor,
                coroutineScope = scope,
                initialMessages = restoredMessages.toChatMessages(),
                initialStickyFacts = restoredFacts.toStickyFacts(),
                initialBranches = restoredBranches.toChatBranches(),
                initialSystemPrompt = initialSystemPrompt,
                initialPrompt = initialPrompt,
                isSystemPromptReadOnly = isSystemPromptReadOnly,
                toolProvider = toolProvider,
                ragQueryInteractor = ragQueryInteractor,
                ragRerankerFactory = ragRerankerFactory,
                persistMessage = { message ->
                    historyInteractor.add(listOf(message).toHistoryMessages().single()).toChatMessages()
                },
            )
        }

        fun createRestoredTab(
            tabNumber: Int,
            storageType: ChatStorageType,
            initialMessages: List<HistoryMessage>,
            initialSystemPrompt: String = "",
            taskSession: TaskSessionSnapshot? = null,
        ): ChatTab {
            val chatViewModel = createChatViewModel(
                tabNumber = tabNumber,
                storageType = storageType,
                initialMessages = initialMessages,
                initialSystemPrompt = initialSystemPrompt,
            )
            chatViewModel.loadModels()
            return ChatTab(
                number = tabNumber,
                title = initialMessages.toTabTitle(),
                viewModel = chatViewModel,
                taskSession = taskSession?.let { snapshot ->
                    TaskModeSession(
                        isModeEnabled = snapshot.isModeEnabled,
                        context = snapshot.context,
                        selectedStage = snapshot.selectedStage,
                        chatFocus = snapshot.context.defaultTaskChatFocus(),
                        stageAgents = snapshot.stages.map { session ->
                            val stageViewModel = createChatViewModel(
                                tabNumber = session.chatId,
                                storageType = storageType,
                                useTaskStageHistory = true,
                                initialSystemPrompt = session.systemPrompt,
                                initialPrompt = session.startUserPrompt.takeIf { session.output == null }.orEmpty(),
                                isSystemPromptReadOnly = true,
                            )
                            stageViewModel.loadModels()
                            TaskStageAgent(
                                session = session,
                                viewModel = stageViewModel,
                            )
                        },
                        pendingTransition = snapshot.pendingTransition,
                        pendingRejection = snapshot.pendingRejection,
                    )
                },
            )
        }

        AiChatAppViewModel(
            createChatViewModel = { tabNumber, storageType, systemPrompt ->
                createChatViewModel(
                    tabNumber = tabNumber,
                    storageType = storageType,
                    initialMessages = initialHistoryByTab[tabNumber],
                    initialSystemPrompt = systemPrompt,
                )
            },
            createTaskStageChatViewModel = { chatId, storageType, systemPrompt, initialPrompt ->
                createChatViewModel(
                    tabNumber = chatId,
                    storageType = storageType,
                    useTaskStageHistory = true,
                    initialSystemPrompt = systemPrompt,
                    initialPrompt = initialPrompt,
                    isSystemPromptReadOnly = true,
                )
            },
            createInitialTabTitle = { tabNumber ->
                initialTitlesByTab[tabNumber] ?: ChatTab.NewTitle
            },
            switchStorage = { sourceStorageType, storageType, currentTabs, activeTabNumber, nextTabNumber ->
                val targetStorage = workspaceStorage.createHistoryStorage(storageType)
                val targetTaskStageStorage = workspaceStorage.createTaskStageHistoryStorage(storageType)
                val sourceMemoryRepository = workspaceStorage.createMemoryRepository(sourceStorageType)
                val targetMemoryRepository = workspaceStorage.createMemoryRepository(storageType)
                currentTabs.forEach { tab ->
                    runBlocking {
                        val targetRepository = targetStorage.createRepository(tab.number)
                        targetRepository.replace(tab.viewModel.state.messages.toHistoryMessages())
                        targetRepository.replaceFacts(tab.viewModel.state.stickyFacts.toHistoryFacts())
                        targetRepository.replaceBranches(tab.viewModel.state.branches.toHistoryBranches())
                        tab.taskSession?.stageAgents.orEmpty().forEach { agent ->
                            val targetStageRepository = targetTaskStageStorage.createRepository(agent.session.chatId)
                            targetStageRepository.replace(agent.viewModel.state.messages.toHistoryMessages())
                            targetStageRepository.replaceFacts(agent.viewModel.state.stickyFacts.toHistoryFacts())
                            targetStageRepository.replaceBranches(agent.viewModel.state.branches.toHistoryBranches())
                        }
                    }
                }
                runBlocking {
                    targetMemoryRepository.replaceItems(sourceMemoryRepository.getItems())
                    targetMemoryRepository.saveProfile(sourceMemoryRepository.getProfile())
                    targetMemoryRepository.replaceInvariants(sourceMemoryRepository.getInvariants())
                }
                val taskSnapshotsByTab = currentTabs.mapNotNull { tab ->
                    tab.taskSession?.toSnapshot()?.let { snapshot -> tab.number to snapshot }
                }.toMap()
                val systemPromptsByTab = currentTabs.associate { tab ->
                    tab.number to tab.viewModel.state.systemPrompt
                }

                val currentNumbers = currentTabs.map { it.number }
                val mergedNumbers = mergeTabNumbers(
                    currentTabNumbers = currentNumbers,
                    savedTabNumbers = targetStorage.loadSavedChatIds(),
                )
                val restoredTabs = mergedNumbers.map { tabNumber ->
                    val messages = runBlocking {
                        targetStorage.createRepository(tabNumber).getMessages()
                    }
                    createRestoredTab(
                        tabNumber = tabNumber,
                        storageType = storageType,
                        initialMessages = messages,
                        initialSystemPrompt = systemPromptsByTab[tabNumber].orEmpty(),
                        taskSession = taskSnapshotsByTab[tabNumber],
                    )
                }
                val safeActiveTabNumber = activeTabNumber
                    .takeIf { number -> mergedNumbers.any { it == number } }
                    ?: mergedNumbers.first()
                val safeNextTabNumber = maxOf(
                    nextTabNumber,
                    (mergedNumbers.maxOrNull() ?: 0) + 1,
                )

                StorageSwitchResult(
                    tabs = restoredTabs,
                    activeTabNumber = safeActiveTabNumber,
                    nextTabNumber = safeNextTabNumber,
                )
            },
            initialTabNumbers = initialTabNumbers,
            initialTaskSessionsByTab = initialWorkspace.tabs.mapNotNull { tab ->
                tab.taskSession?.let { taskSession -> tab.number to taskSession }
            }.toMap(),
            initialSystemPromptsByTab = initialWorkspace.tabs.associate { tab ->
                tab.number to tab.systemPrompt
            },
            initialActiveTabNumber = initialWorkspace.activeTabNumber,
            initialNextTabNumber = initialWorkspace.nextTabNumber,
            initialStorageType = initialStorageType,
            storageDirectoryLabel = workspaceStorage.storageDirectoryLabel(),
            onWorkspaceChanged = { tabs, activeTabNumber, nextTabNumber, storageType ->
                workspaceStorage.save(
                    tabs = tabs.map {
                        WorkspaceTabSnapshot(
                            number = it.number,
                            systemPrompt = it.systemPrompt,
                            taskSession = it.taskSession,
                        )
                    },
                    activeTabNumber = activeTabNumber,
                    nextTabNumber = nextTabNumber,
                    selectedStorageType = storageType,
                )
            },
            onTabClosed = { tabNumber, storageType ->
                workspaceStorage.createHistoryStorage(storageType).deleteChat(tabNumber)
                val taskStageStorage = workspaceStorage.createTaskStageHistoryStorage(storageType)
                TaskState.entries.forEach { stage ->
                    taskStageStorage.deleteChat(tabNumber.toTaskStageChatId(stage))
                }
            },
        )
    }
    val settingsViewModel = remember(scope, workspaceStorage, viewModel, profileServices, invariantServices) {
        SettingsViewModel(
            coroutineScope = scope,
            loadProfile = {
                workspaceStorage.createMemoryRepository(viewModel.state.selectedStorageType).getProfile().text
            },
            saveProfile = { text ->
                workspaceStorage
                    .createMemoryRepository(viewModel.state.selectedStorageType)
                    .saveProfile(UserProfile(text = text))
                    .text
            },
            updateProfileFromInterview = { currentProfile, answers ->
                val activeChatViewModel = viewModel.state.activeTab?.viewModel
                val providerName = activeChatViewModel?.selectedModelProviderName.orEmpty()
                val modelId = activeChatViewModel?.selectedModelId.orEmpty()
                val profileService = profileServices[providerName]
                    ?: error("Неизвестный провайдер профиля: $providerName")
                profileService.updateProfile(
                    currentProfile = UserProfile(text = currentProfile),
                    interviewAnswers = answers,
                    modelId = modelId.takeIf { it.isNotBlank() } ?: error("Не выбрана модель для интервью."),
                ).text
            },
            loadInvariants = {
                workspaceStorage.createMemoryRepository(viewModel.state.selectedStorageType).getInvariants()
            },
            saveInvariants = { invariants ->
                workspaceStorage
                    .createMemoryRepository(viewModel.state.selectedStorageType)
                    .replaceInvariants(invariants)
            },
            updateInvariantsFromChat = { currentInvariants, chatMessages ->
                val activeChatViewModel = viewModel.state.activeTab?.viewModel
                val providerName = activeChatViewModel?.selectedModelProviderName.orEmpty()
                val modelId = activeChatViewModel?.selectedModelId.orEmpty()
                val invariantService = invariantServices[providerName]
                    ?: error("Неизвестный провайдер инвариантов: $providerName")
                invariantService.updateInvariants(
                    currentInvariants = currentInvariants,
                    chatMessages = chatMessages,
                    modelId = modelId.takeIf { it.isNotBlank() } ?: error("Не выбрана модель для сбора инвариантов."),
                )
            },
            initialMcpServers = workspaceStorage.loadMcpServers(),
            onMcpServersChanged = { servers ->
                workspaceStorage.saveMcpServers(servers)
            },
        )
    }

    AiChatAppScreen(
        state = viewModel.state,
        onEvent = viewModel::onEvent,
        onSettingsClicked = { settingsViewModel.onEvent(SettingsEvent.SettingsDialogOpened) },
    )
    SettingsDialogs(
        state = settingsViewModel.state,
        onEvent = settingsViewModel::onEvent,
    )
}

private interface AppChatHistoryStorage {
    fun loadSavedChatIds(): List<Int>
    fun createRepository(chatId: Int): ChatHistoryRepository
    fun deleteChat(chatId: Int)
}

private fun WorkspaceStorage.createHistoryStorage(storageType: ChatStorageType): AppChatHistoryStorage =
    when (storageType) {
        ChatStorageType.Json -> JsonAppChatHistoryStorage(
            storage = JsonFileChatHistoryStorage(jsonHistoryFile()),
        )
        ChatStorageType.Database -> DatabaseAppChatHistoryStorage(
            storage = SqldelightChatHistoryStorage(databaseHistoryFile()),
        )
    }

private fun WorkspaceStorage.createTaskStageHistoryStorage(storageType: ChatStorageType): AppChatHistoryStorage =
    when (storageType) {
        ChatStorageType.Json -> JsonAppChatHistoryStorage(
            storage = JsonFileChatHistoryStorage(jsonTaskStageHistoryFile()),
        )
        ChatStorageType.Database -> DatabaseAppChatHistoryStorage(
            storage = SqldelightChatHistoryStorage(databaseTaskStageHistoryFile()),
        )
    }

private fun WorkspaceStorage.createMemoryRepository(storageType: ChatStorageType): AssistantMemoryRepository =
    when (storageType) {
        ChatStorageType.Json -> JsonFileAssistantMemoryRepository(jsonMemoryFile())
        ChatStorageType.Database -> SqldelightAssistantMemoryRepository(databaseHistoryFile())
    }

private class JsonAppChatHistoryStorage(
    private val storage: JsonFileChatHistoryStorage,
) : AppChatHistoryStorage {
    override fun loadSavedChatIds(): List<Int> =
        storage.loadSavedChatIds()

    override fun createRepository(chatId: Int): ChatHistoryRepository =
        storage.createRepository(chatId)

    override fun deleteChat(chatId: Int) {
        storage.deleteChat(chatId)
    }
}

private class DatabaseAppChatHistoryStorage(
    private val storage: SqldelightChatHistoryStorage,
) : AppChatHistoryStorage {
    override fun loadSavedChatIds(): List<Int> =
        storage.loadSavedChatIds()

    override fun createRepository(chatId: Int): ChatHistoryRepository =
        storage.createRepository(chatId)

    override fun deleteChat(chatId: Int) {
        storage.deleteChat(chatId)
    }
}

private fun mergeTabNumbers(
    currentTabNumbers: List<Int>,
    savedTabNumbers: List<Int>,
): List<Int> {
    val current = currentTabNumbers
        .filter { it > 0 }
        .distinct()
        .ifEmpty { listOf(1) }
    val currentSet = current.toSet()
    return current + savedTabNumbers
        .filter { it > 0 && it !in currentSet }
        .distinct()
        .sorted()
}

private fun List<HistoryMessage>.toTabTitle(): String {
    val firstPrompt = firstOrNull { it.role == HistoryRole.User }?.content.orEmpty()
    val words = firstPrompt
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
    return words
        .take(MaxTabTitleWords)
        .joinToString(separator = " ")
        .ifBlank { ChatTab.NewTitle }
}

private fun Int.toTaskStageChatId(stage: TaskState): Int =
    this * TaskStageChatIdMultiplier + stage.ordinal + 1

private const val MaxTabTitleWords = 5
private const val TaskStageChatIdMultiplier = 10
