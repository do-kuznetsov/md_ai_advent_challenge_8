package com.sibgear.deepseek.chat.workspace.ui.external.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sibgear.deepseek.chat.domain.model.TaskState
import com.sibgear.deepseek.chat.domain.model.previous
import com.sibgear.deepseek.chat.ui.external.view.AiModelSettingsDrawer
import com.sibgear.deepseek.chat.ui.external.view.ChatPane
import com.sibgear.deepseek.chat.ui.external.view.ChatScreen
import com.sibgear.deepseek.chat.workspace.ui.external.model.AiChatAppEvent
import com.sibgear.deepseek.chat.workspace.ui.external.model.AiChatAppViewState
import com.sibgear.deepseek.chat.workspace.ui.external.model.ChatTab
import com.sibgear.deepseek.chat.workspace.ui.external.model.TaskModeSession
import com.sibgear.deepseek.chat.workspace.ui.internal.view.ChatTabBar
import com.sibgear.deepseek.chat.workspace.ui.internal.view.UserProfileDialog

@Composable
fun AiChatAppScreen(
    state: AiChatAppViewState,
    onEvent: (AiChatAppEvent) -> Unit,
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
                        onProfileClicked = { onEvent(AiChatAppEvent.ProfileDialogOpened) },
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

            if (state.isProfileDialogOpen) {
                UserProfileDialog(
                    profileDraft = state.profileDraft,
                    isActionEnabled = state.isProfileActionEnabled,
                    isInterviewActive = state.isProfileInterviewActive,
                    interviewQuestionIndex = state.profileInterviewQuestionIndex,
                    interviewAnswerInput = state.profileInterviewAnswerInput,
                    isInterviewLoading = state.isProfileInterviewLoading,
                    error = state.profileError,
                    onDismissRequest = { onEvent(AiChatAppEvent.ProfileDialogClosed) },
                    onProfileChanged = { onEvent(AiChatAppEvent.ProfileDraftChanged(it)) },
                    onSaveClicked = { onEvent(AiChatAppEvent.ProfileSaved) },
                    onInterviewClicked = { onEvent(AiChatAppEvent.ProfileInterviewStarted) },
                    onInterviewAnswerChanged = { onEvent(AiChatAppEvent.ProfileInterviewAnswerChanged(it)) },
                    onInterviewAnswerSubmitted = { onEvent(AiChatAppEvent.ProfileInterviewAnswerSubmitted) },
                )
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

                    TaskTransitionControls(
                        taskSession = taskSession,
                        onAccept = { onEvent(AiChatAppEvent.TaskTransitionAccepted) },
                        onRevise = { onEvent(AiChatAppEvent.TaskTransitionRevisionRequested) },
                        onBack = { onEvent(AiChatAppEvent.TaskPreviousStageRequested) },
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
                            ChatPane(
                                state = tab.viewModel.state,
                                onEvent = { onEvent(AiChatAppEvent.ActiveChatEvent(it)) },
                                modifier = Modifier.weight(chatSplitFraction).fillMaxHeight(),
                                showTaskModeToggle = true,
                                isTaskModeEnabled = true,
                                onTaskModeToggled = { onEvent(AiChatAppEvent.TaskModeToggled) },
                            )

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
                                ChatPane(
                                    state = agent.viewModel.state,
                                    onEvent = { onEvent(AiChatAppEvent.ActiveTaskStageChatEvent(it)) },
                                    modifier = Modifier.weight(1f - chatSplitFraction).fillMaxHeight(),
                                )
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
    onRevise: () -> Unit,
    onBack: () -> Unit,
) {
    val context = taskSession.context ?: return
    val pending = taskSession.pendingTransition
    val isCurrentStageLoading = taskSession.stageAgents
        .firstOrNull { it.session.state == context.state }
        ?.viewModel
        ?.state
        ?.isLoading == true
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (pending != null) {
            Text(
                text = "${pending.from.title} -> ${pending.to.title}",
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            Button(onClick = onAccept) {
                Text("Accept")
            }
            OutlinedButton(onClick = onRevise) {
                Text("Revise")
            }
        }

        if (context.state.previous() != null) {
            TextButton(
                onClick = onBack,
                enabled = !isCurrentStageLoading,
            ) {
                Text("Back")
            }
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
