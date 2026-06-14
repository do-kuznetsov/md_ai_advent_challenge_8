package com.sibgear.deepseek.chat.ui.internal.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sibgear.deepseek.chat.domain.model.ContextManagementMode
import com.sibgear.deepseek.chat.ui.external.model.ChatEvent
import com.sibgear.deepseek.chat.ui.external.model.ChatViewState

@Composable
internal fun ContextManagementPanel(
    state: ChatViewState,
    onEvent: (ChatEvent) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.contextUsageLabel,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            OutlinedButton(
                onClick = {
                    onEvent(
                        ChatEvent.ContextManagementPanelExpandedChanged(
                            isExpanded = !state.isContextManagementPanelExpanded,
                        ),
                    )
                },
            ) {
                Text(
                    text = "${state.contextManagementMode.displayTitle()} ${state.contextManagementArrow()}",
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }

        AnimatedVisibility(visible = state.isContextManagementPanelExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ModeRow(
                    mode = ContextManagementMode.None,
                    selectedMode = state.contextManagementMode,
                    onSelected = { onEvent(ChatEvent.ContextManagementModeSelected(it)) },
                ) {
                    Text(
                        text = "никаких изменений",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                ModeRow(
                    mode = ContextManagementMode.ContextSummary,
                    selectedMode = state.contextManagementMode,
                    onSelected = { onEvent(ChatEvent.ContextManagementModeSelected(it)) },
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("сжимать историю каждые")
                        ShortcutOutlinedTextField(
                            value = state.summaryIntervalInput,
                            onValueChange = { onEvent(ChatEvent.SummaryIntervalChanged(it)) },
                            modifier = Modifier.width(56.dp),
                            singleLine = true,
                            enabled = state.contextManagementMode == ContextManagementMode.ContextSummary,
                        )
                        Text("сообщений")
                    }
                }

                ModeRow(
                    mode = ContextManagementMode.SlidingWindow,
                    selectedMode = state.contextManagementMode,
                    onSelected = { onEvent(ChatEvent.ContextManagementModeSelected(it)) },
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("учитывать только последние")
                        ShortcutOutlinedTextField(
                            value = state.slidingWindowMessagesInput,
                            onValueChange = { onEvent(ChatEvent.SlidingWindowMessagesChanged(it)) },
                            modifier = Modifier.width(56.dp),
                            singleLine = true,
                            enabled = state.contextManagementMode == ContextManagementMode.SlidingWindow,
                        )
                        Text("сообщений")
                    }
                }

                ModeRow(
                    mode = ContextManagementMode.StickyFacts,
                    selectedMode = state.contextManagementMode,
                    onSelected = { onEvent(ChatEvent.ContextManagementModeSelected(it)) },
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("facts + последние")
                        ShortcutOutlinedTextField(
                            value = state.stickyFactsWindowInput,
                            onValueChange = { onEvent(ChatEvent.StickyFactsWindowMessagesChanged(it)) },
                            modifier = Modifier.width(56.dp),
                            singleLine = true,
                            enabled = state.contextManagementMode == ContextManagementMode.StickyFacts,
                        )
                        Text("сообщений")
                    }
                }

            }
        }
    }
}

@Composable
private fun ModeRow(
    mode: ContextManagementMode,
    selectedMode: ContextManagementMode,
    onSelected: (ContextManagementMode) -> Unit,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selectedMode == mode,
            onClick = { onSelected(mode) },
        )
        Text(
            text = mode.displayTitle(),
            modifier = Modifier.width(132.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        content()
    }
}

private fun ContextManagementMode.displayTitle(): String =
    when (this) {
        ContextManagementMode.None -> "Без управления"
        ContextManagementMode.ContextSummary -> "Context Summary"
        ContextManagementMode.SlidingWindow -> "Sliding Window"
        ContextManagementMode.StickyFacts -> "Sticky Facts"
    }

private fun ChatViewState.contextManagementArrow(): String =
    if (isContextManagementPanelExpanded) "↓" else "↑"
