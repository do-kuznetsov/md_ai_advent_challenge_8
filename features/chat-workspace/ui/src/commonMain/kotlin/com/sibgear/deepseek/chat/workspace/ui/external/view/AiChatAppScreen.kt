package com.sibgear.deepseek.chat.workspace.ui.external.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sibgear.deepseek.chat.domain.model.TaskStageResultStatus
import com.sibgear.deepseek.chat.domain.model.TaskState
import com.sibgear.deepseek.chat.ui.external.view.AiModelSettingsDrawer
import com.sibgear.deepseek.chat.ui.external.view.ChatPane
import com.sibgear.deepseek.chat.ui.external.view.ChatScreen
import com.sibgear.deepseek.chat.workspace.ui.external.model.AiChatAppEvent
import com.sibgear.deepseek.chat.workspace.ui.external.model.AiChatAppViewState
import com.sibgear.deepseek.chat.workspace.ui.external.model.ChatTab
import com.sibgear.deepseek.chat.workspace.ui.external.model.TaskChatFocus
import com.sibgear.deepseek.chat.workspace.ui.external.model.TaskModeSession
import com.sibgear.deepseek.chat.workspace.ui.internal.view.ChatTabBar

@Composable
fun AiChatAppScreen(
    state: AiChatAppViewState,
    onEvent: (AiChatAppEvent) -> Unit,
    onSettingsClicked: () -> Unit,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            var tabsWidthFraction by remember { mutableFloatStateOf(DefaultTabsWidthFraction) }
            val density = LocalDensity.current

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val windowWidthPx = with(density) { maxWidth.toPx() }
                val tabsWidth = maxWidth * tabsWidthFraction

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    ChatTabBar(
                        tabs = state.tabs,
                        activeTabNumber = state.activeTabNumber,
                        selectedStorageType = state.selectedStorageType,
                        storageDirectoryLabel = state.storageDirectoryLabel,
                        isStorageMenuExpanded = state.isStorageMenuExpanded,
                        isStorageSwitchEnabled = state.isStorageSwitchEnabled,
                        onTabSelected = { onEvent(AiChatAppEvent.TabSelected(it)) },
                        onTabClosed = { onEvent(AiChatAppEvent.TabClosed(it)) },
                        onTabAdded = { onEvent(AiChatAppEvent.TabAdded) },
                        onStorageMenuExpandedChanged = {
                            onEvent(AiChatAppEvent.StorageMenuExpandedChanged(it))
                        },
                        onStorageSelected = { onEvent(AiChatAppEvent.StorageSelected(it)) },
                        onSettingsClicked = onSettingsClicked,
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(tabsWidth)
                            .padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(6.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                            .pointerInput(windowWidthPx) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    tabsWidthFraction = (tabsWidthFraction + dragAmount.x / windowWidthPx)
                                        .coerceIn(MinTabsWidthFraction, MaxTabsWidthFraction)
                                }
                            },
                    )

                    state.activeTab?.let { tab ->
                        if (tab.taskSession?.isModeEnabled == true) {
                            TaskStateMachineChatScreen(
                                tab = tab,
                                onEvent = onEvent,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            ChatScreen(
                                state = tab.viewModel.state,
                                onEvent = { onEvent(AiChatAppEvent.ActiveChatEvent(it)) },
                                modifier = Modifier.weight(1f),
                                showTaskModeToggle = true,
                                isTaskModeEnabled = false,
                                onTaskModeToggled = { onEvent(AiChatAppEvent.TaskModeToggled) },
                            )
                        }
                    }
                }
            }

        }
    }
}

@Composable
private fun TaskStateMachineChatScreen(
    tab: ChatTab,
    onEvent: (AiChatAppEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val taskSession = tab.taskSession ?: return
    var chatSplitFraction by rememberSaveable { mutableFloatStateOf(DefaultTaskChatSplitFraction) }
    var isAiModelDrawerExpanded by rememberSaveable { mutableStateOf(false) }
    var aiModelDrawerWidthFraction by rememberSaveable { mutableFloatStateOf(DefaultAiModelDrawerWidthFraction) }
    val density = LocalDensity.current

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val maxWidthPx = with(density) { maxWidth.toPx() }
            val drawerWidth = if (isAiModelDrawerExpanded) {
                maxWidth * aiModelDrawerWidthFraction
            } else {
                AiModelDrawerTabWidth
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TaskStageStrip(
                        taskSession = taskSession,
                        onStageSelected = { onEvent(AiChatAppEvent.TaskStageSelected(it)) },
                    )

                    TaskStageTitleRow(
                        title = taskSession.selectedStage.title,
                        chatSplitFraction = chatSplitFraction,
                    )

                    BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        val chatAreaWidthPx = with(density) { maxWidth.toPx() }

                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FocusedChatPaneFrame(
                                isFocused = taskSession.chatFocus == TaskChatFocus.Orchestrator,
                                modifier = Modifier.weight(chatSplitFraction).fillMaxHeight(),
                            ) {
                                ChatPane(
                                    state = tab.viewModel.state,
                                    onEvent = { onEvent(AiChatAppEvent.ActiveChatEvent(it)) },
                                    modifier = Modifier.fillMaxSize(),
                                    showTaskModeToggle = true,
                                    isTaskModeEnabled = true,
                                    onTaskModeToggled = { onEvent(AiChatAppEvent.TaskModeToggled) },
                                    isPromptInputEnabled = !taskSession.isOrchestratorFsmFlowRunning,
                                    isPromptInputLoading = taskSession.isOrchestratorFsmFlowRunning,
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .width(TaskChatSplitHandleWidth)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                                    .pointerInput(chatAreaWidthPx) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            chatSplitFraction = (chatSplitFraction + dragAmount.x / chatAreaWidthPx)
                                                .coerceIn(MinTaskChatSplitFraction, MaxTaskChatSplitFraction)
                                        }
                                    },
                            )

                            taskSession.selectedStageAgent?.let { agent ->
                                val isSelectedStageActive = agent.session.state == taskSession.context?.state
                                val isStageFocused = taskSession.chatFocus == TaskChatFocus.Stage(agent.session.state) &&
                                    isSelectedStageActive
                                FocusedChatPaneFrame(
                                    isFocused = isStageFocused,
                                    isWorking = isStageFocused && agent.viewModel.state.isLoading,
                                    modifier = Modifier.weight(1f - chatSplitFraction).fillMaxHeight(),
                                ) {
                                    ChatPane(
                                        state = agent.viewModel.state,
                                        onEvent = { onEvent(AiChatAppEvent.ActiveTaskStageChatEvent(it)) },
                                        modifier = Modifier.fillMaxSize(),
                                        leadingSystemPrompt = agent.viewModel.state.systemPrompt,
                                        isPromptInputEnabled = isSelectedStageActive,
                                        promptHeaderContent = {
                                            if (isSelectedStageActive) {
                                                TaskTransitionControls(
                                                    taskSession = taskSession,
                                                    onAccept = { onEvent(AiChatAppEvent.TaskTransitionAccepted) },
                                                    onReject = { onEvent(AiChatAppEvent.TaskStageRejected) },
                                                )
                                            }
                                        },
                                    )
                                }
                            } ?: Box(
                                modifier = Modifier
                                    .weight(1f - chatSplitFraction)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                        }
                    }
                }

                AiModelSettingsDrawer(
                    state = tab.viewModel.state,
                    onEvent = { onEvent(AiChatAppEvent.ActiveChatEvent(it)) },
                    isExpanded = isAiModelDrawerExpanded,
                    onExpandedChange = { isAiModelDrawerExpanded = it },
                    onResize = { dragDeltaPx ->
                        val deltaFraction = dragDeltaPx / maxWidthPx
                        aiModelDrawerWidthFraction = (aiModelDrawerWidthFraction - deltaFraction)
                            .coerceIn(AiModelDrawerMinWidthFraction, AiModelDrawerMaxWidthFraction)
                    },
                    modifier = Modifier.width(drawerWidth).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun FocusedChatPaneFrame(
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    isWorking: Boolean = false,
    content: @Composable () -> Unit,
) {
    val borderColor = when {
        !isFocused -> Color.Transparent
        isWorking -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
    }
    Box(
        modifier = modifier.border(
            width = FocusedChatPaneBorderWidth,
            color = borderColor,
            shape = RoundedCornerShape(FocusedChatPaneCornerRadius),
        ),
    ) {
        content()
    }
}

@Composable
private fun TaskStageTitleRow(
    title: String,
    chatSplitFraction: Float,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.weight(chatSplitFraction))
        Box(modifier = Modifier.width(TaskChatSplitHandleWidth))
        Text(
            text = title,
            modifier = Modifier.weight(1f - chatSplitFraction),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TaskStageStrip(
    taskSession: TaskModeSession,
    onStageSelected: (TaskState) -> Unit,
) {
    val currentStage = taskSession.context?.state ?: TaskState.Planning
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TaskState.entries.forEachIndexed { index, stage ->
            val isReached = taskSession.stageAgents.any { it.session.state == stage } ||
                stage.ordinal <= currentStage.ordinal
            Text(
                text = stage.title,
                modifier = Modifier.clickable(enabled = isReached) { onStageSelected(stage) },
                color = when {
                    stage == currentStage -> MaterialTheme.colorScheme.primary
                    isReached -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (stage == currentStage) FontWeight.Bold else FontWeight.Normal,
            )
            if (index < TaskState.entries.lastIndex) {
                Text(
                    text = "->",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun TaskTransitionControls(
    taskSession: TaskModeSession,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    val context = taskSession.context ?: return
    val currentAgent = taskSession.stageAgents.firstOrNull { it.session.state == context.state } ?: return
    val isCurrentStageReady = currentAgent.viewModel.state.isLoading != true &&
        currentAgent.session.isReadyForTransition
    if (!isCurrentStageReady) {
        return
    }
    val statusLabel = when (currentAgent.session.resultStatus) {
        TaskStageResultStatus.Completed -> "${context.state.title} result"
        TaskStageResultStatus.Blocked -> "${context.state.title} blocked"
        TaskStageResultStatus.InProgress,
        TaskStageResultStatus.NeedsUserInput -> return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = statusLabel,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
        Button(onClick = onAccept) {
            Text("Принять")
        }
        OutlinedButton(onClick = onReject) {
            Text("Отклонить")
        }
    }
}

private const val DefaultTabsWidthFraction = 0.2f
private const val MinTabsWidthFraction = 0.12f
private const val MaxTabsWidthFraction = 0.45f
private const val DefaultTaskChatSplitFraction = 0.52f
private const val MinTaskChatSplitFraction = 0.30f
private const val MaxTaskChatSplitFraction = 0.75f
private const val DefaultAiModelDrawerWidthFraction = 0.25f
private const val AiModelDrawerMinWidthFraction = 0.16f
private const val AiModelDrawerMaxWidthFraction = 0.30f
private val TaskChatSplitHandleWidth: Dp = 6.dp
private val AiModelDrawerTabWidth: Dp = 38.dp
private val FocusedChatPaneBorderWidth: Dp = 2.dp
private val FocusedChatPaneCornerRadius: Dp = 8.dp
