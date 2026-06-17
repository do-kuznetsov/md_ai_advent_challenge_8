package com.sibgear.deepseek.chat.workspace.ui.external.view

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.sibgear.deepseek.chat.ui.external.view.ChatScreen
import com.sibgear.deepseek.chat.workspace.ui.external.model.AiChatAppEvent
import com.sibgear.deepseek.chat.workspace.ui.external.model.AiChatAppViewState
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
                        ChatScreen(
                            state = tab.viewModel.state,
                            onEvent = { onEvent(AiChatAppEvent.ActiveChatEvent(it)) },
                            modifier = Modifier.weight(1f),
                        )
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

private const val DefaultTabsWidthFraction = 0.2f
private const val MinTabsWidthFraction = 0.12f
private const val MaxTabsWidthFraction = 0.45f
