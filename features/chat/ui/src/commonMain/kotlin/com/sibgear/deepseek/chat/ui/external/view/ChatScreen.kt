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
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
import com.sibgear.deepseek.chat.ui.internal.view.RagSettingsPanel
import com.sibgear.deepseek.chat.ui.internal.view.StickyFactsPanel

enum class ChatSettingsDrawerType {
    AiModel,
    Rag,
}

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
            var activeSettingsDrawer by rememberSaveable { mutableStateOf<ChatSettingsDrawerType?>(null) }
            var settingsDrawerWidthFraction by rememberSaveable { mutableStateOf(0.25f) }
            val drawerWidth = if (activeSettingsDrawer != null) {
                maxWidth * settingsDrawerWidthFraction
            } else {
                SettingsDrawerClosedWidth
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
                    ChatSettingsDrawer(
                        state = state,
                        onEvent = onEvent,
                        activeDrawer = activeSettingsDrawer,
                        onActiveDrawerChanged = { activeSettingsDrawer = it },
                        onResize = { dragDeltaPx ->
                            val deltaFraction = dragDeltaPx / maxWidthPx
                            settingsDrawerWidthFraction = (settingsDrawerWidthFraction - deltaFraction)
                                .coerceIn(SettingsDrawerMinWidthFraction, SettingsDrawerMaxWidthFraction)
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
    isPromptInputLoading: Boolean = false,
) {
    val effectiveLeadingSystemPrompt = leadingSystemPrompt
        ?: state.systemPrompt.takeIf { state.messages.isNotEmpty() && it.isNotBlank() }

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
            leadingSystemPrompt = effectiveLeadingSystemPrompt,
        )

        PromptInputArea(
            state = state,
            onEvent = onEvent,
            showTaskModeToggle = showTaskModeToggle,
            isTaskModeEnabled = isTaskModeEnabled,
            onTaskModeToggled = onTaskModeToggled,
            promptHeaderContent = promptHeaderContent,
            isPromptInputEnabled = isPromptInputEnabled,
            isPromptInputLoading = isPromptInputLoading,
        )
    }
}

@Composable
fun ChatSettingsDrawer(
    state: ChatViewState,
    onEvent: (ChatEvent) -> Unit,
    activeDrawer: ChatSettingsDrawerType?,
    onActiveDrawerChanged: (ChatSettingsDrawerType?) -> Unit,
    onResize: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        Column(
            modifier = Modifier.width(SettingsDrawerTabWidth).fillMaxHeight(),
        ) {
            SettingsDrawerTab(
                title = "AI модель",
                isExpanded = activeDrawer == ChatSettingsDrawerType.AiModel,
                onClick = {
                    onActiveDrawerChanged(
                        ChatSettingsDrawerType.AiModel.toggleFrom(activeDrawer),
                    )
                },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            SettingsDrawerTab(
                title = "RAG",
                isExpanded = activeDrawer == ChatSettingsDrawerType.Rag,
                onClick = {
                    onActiveDrawerChanged(
                        ChatSettingsDrawerType.Rag.toggleFrom(activeDrawer),
                    )
                },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }

        if (activeDrawer != null) {
            Box(
                modifier = Modifier
                    .width(SettingsDrawerResizeHandleWidth)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta -> onResize(delta) },
                    ),
            )

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                when (activeDrawer) {
                    ChatSettingsDrawerType.AiModel -> {
                        ApiSettingsPanel(
                            state = state,
                            onEvent = onEvent,
                            modifier = Modifier.fillMaxHeight(),
                        )
                    }
                    ChatSettingsDrawerType.Rag -> {
                        RagSettingsPanel(
                            state = state,
                            onEvent = onEvent,
                            modifier = Modifier.fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsDrawerTab(
    title: String,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeBackgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val backgroundColor = if (isExpanded) {
        activeBackgroundColor
    } else {
        lerp(activeBackgroundColor, Color.Black, 0.22f)
    }
    val textColor = if (isExpanded) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
    }

    BoxWithConstraints(
        modifier = modifier
            .background(backgroundColor)
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
                text = if (isExpanded) "$title ↓" else "$title ↑",
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
                color = textColor,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

private fun ChatSettingsDrawerType.toggleFrom(
    activeDrawer: ChatSettingsDrawerType?,
): ChatSettingsDrawerType? =
    if (activeDrawer == this) null else this

private val SettingsDrawerTabWidth: Dp = 38.dp
private val SettingsDrawerClosedWidth: Dp = SettingsDrawerTabWidth
private val SettingsDrawerResizeHandleWidth: Dp = 6.dp
private const val SettingsDrawerMinWidthFraction = 0.16f
private const val SettingsDrawerMaxWidthFraction = 0.30f
