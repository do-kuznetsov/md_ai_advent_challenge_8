package com.sibgear.deepseek.chat.ui.external.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sibgear.deepseek.chat.ui.external.model.ChatEvent
import com.sibgear.deepseek.chat.ui.external.model.ChatViewState
import com.sibgear.deepseek.chat.ui.internal.view.ApiSettingsPanel
import com.sibgear.deepseek.chat.ui.internal.view.BranchTreePanel
import com.sibgear.deepseek.chat.ui.internal.view.ChatArea
import com.sibgear.deepseek.chat.ui.internal.view.PromptInputArea
import com.sibgear.deepseek.chat.ui.internal.view.StickyFactsPanel

@Composable
fun ChatScreen(
    state: ChatViewState,
    onEvent: (ChatEvent) -> Unit,
    modifier: Modifier = Modifier,
    showModelDrawer: Boolean = true,
    showTaskModeToggle: Boolean = false,
    isTaskModeEnabled: Boolean = false,
    onTaskModeToggled: () -> Unit = {},
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val density = LocalDensity.current
            val maxWidthPx = with(density) { maxWidth.toPx() }
            var isAiModelDrawerExpanded by rememberSaveable { mutableStateOf(false) }
            var aiModelDrawerWidthFraction by rememberSaveable { mutableStateOf(0.25f) }
            val drawerWidth = if (isAiModelDrawerExpanded) {
                maxWidth * aiModelDrawerWidthFraction
            } else {
                AiModelDrawerTabWidth
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ChatPane(
                    state = state,
                    onEvent = onEvent,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    showTaskModeToggle = showTaskModeToggle,
                    isTaskModeEnabled = isTaskModeEnabled,
                    onTaskModeToggled = onTaskModeToggled,
                )

                if (showModelDrawer) {
                    AiModelSettingsDrawer(
                        state = state,
                        onEvent = onEvent,
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
}

@Composable
fun ChatPane(
    state: ChatViewState,
    onEvent: (ChatEvent) -> Unit,
    modifier: Modifier = Modifier,
    showTaskModeToggle: Boolean = false,
    isTaskModeEnabled: Boolean = false,
    onTaskModeToggled: () -> Unit = {},
    leadingSystemPrompt: String? = null,
    promptHeaderContent: (@Composable () -> Unit)? = null,
    isPromptInputEnabled: Boolean = true,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StickyFactsPanel(state = state)
        BranchTreePanel(state = state)

        ChatArea(
            messages = state.messages,
            pinnedContextMessageIndex = state.pinnedContextMessageIndex,
            expandedCompressionMessageIndexes = state.expandedCompressionMessageIndexes,
            onCompressionSummaryToggled = { onEvent(ChatEvent.CompressionSummaryToggled(it)) },
            modifier = Modifier.weight(1f),
            leadingSystemPrompt = leadingSystemPrompt,
        )

        PromptInputArea(
            state = state,
            onEvent = onEvent,
            showTaskModeToggle = showTaskModeToggle,
            isTaskModeEnabled = isTaskModeEnabled,
            onTaskModeToggled = onTaskModeToggled,
            promptHeaderContent = promptHeaderContent,
            isPromptInputEnabled = isPromptInputEnabled,
        )
    }
}

@Composable
fun AiModelSettingsDrawer(
    state: ChatViewState,
    onEvent: (ChatEvent) -> Unit,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onResize: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        AiModelDrawerTab(
            isExpanded = isExpanded,
            onClick = { onExpandedChange(!isExpanded) },
            modifier = Modifier.width(AiModelDrawerTabWidth).fillMaxHeight(),
        )

        if (isExpanded) {
            Box(
                modifier = Modifier
                    .width(AiModelDrawerResizeHandleWidth)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta -> onResize(delta) },
                    ),
            )

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                ApiSettingsPanel(
                    state = state,
                    onEvent = onEvent,
                    modifier = Modifier.fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun AiModelDrawerTab(
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .width(maxHeight)
                .graphicsLayer(rotationZ = -90f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isExpanded) "AI модель ↓" else "AI модель ↑",
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

private val AiModelDrawerTabWidth: Dp = 38.dp
private val AiModelDrawerResizeHandleWidth: Dp = 6.dp
private const val AiModelDrawerMinWidthFraction = 0.16f
private const val AiModelDrawerMaxWidthFraction = 0.30f
