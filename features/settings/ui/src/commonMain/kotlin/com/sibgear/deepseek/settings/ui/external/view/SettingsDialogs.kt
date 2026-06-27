package com.sibgear.deepseek.settings.ui.external.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sibgear.deepseek.settings.ui.external.model.SettingsEvent
import com.sibgear.deepseek.settings.ui.external.model.SettingsViewState
import com.sibgear.deepseek.settings.ui.internal.view.ProjectInvariantsDialog
import com.sibgear.deepseek.settings.ui.internal.view.UserProfileDialog

@Composable
fun SettingsDialogs(
    state: SettingsViewState,
    onEvent: (SettingsEvent) -> Unit,
) {
    if (state.isSettingsDialogOpen) {
        SettingsDialog(
            onDismissRequest = { onEvent(SettingsEvent.SettingsDialogClosed) },
            onProfileClicked = { onEvent(SettingsEvent.ProfileDialogOpened) },
            onInvariantsClicked = { onEvent(SettingsEvent.InvariantsDialogOpened) },
        )
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
            onDismissRequest = { onEvent(SettingsEvent.ProfileDialogClosed) },
            onProfileChanged = { onEvent(SettingsEvent.ProfileDraftChanged(it)) },
            onSaveClicked = { onEvent(SettingsEvent.ProfileSaved) },
            onInterviewClicked = { onEvent(SettingsEvent.ProfileInterviewStarted) },
            onInterviewAnswerChanged = { onEvent(SettingsEvent.ProfileInterviewAnswerChanged(it)) },
            onInterviewAnswerSubmitted = { onEvent(SettingsEvent.ProfileInterviewAnswerSubmitted) },
        )
    }

    if (state.isInvariantsDialogOpen) {
        ProjectInvariantsDialog(
            invariantsDraft = state.invariantsDraft,
            isActionEnabled = state.isInvariantsActionEnabled,
            chatMessages = state.invariantsChatMessages,
            chatInput = state.invariantsChatInput,
            isApplying = state.isInvariantsApplying,
            error = state.invariantsError,
            onDismissRequest = { onEvent(SettingsEvent.InvariantsDialogClosed) },
            onInvariantsChanged = { onEvent(SettingsEvent.InvariantsDraftChanged(it)) },
            onSaveClicked = { onEvent(SettingsEvent.InvariantsSaved) },
            onApplyClicked = { onEvent(SettingsEvent.InvariantsApplied) },
            onChatInputChanged = { onEvent(SettingsEvent.InvariantsChatInputChanged(it)) },
            onChatMessageSent = { onEvent(SettingsEvent.InvariantsChatMessageSent) },
        )
    }
}

@Composable
private fun SettingsDialog(
    onDismissRequest: () -> Unit,
    onProfileClicked: () -> Unit,
    onInvariantsClicked: () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier.widthIn(min = 320.dp, max = 420.dp),
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Настройки",
                    style = MaterialTheme.typography.titleMedium,
                )

                SettingsActionButton(
                    text = "Профиль",
                    icon = { Icon(imageVector = Icons.Default.Person, contentDescription = null) },
                    onClick = onProfileClicked,
                )

                SettingsActionButton(
                    text = "Правила проекта",
                    icon = { Icon(imageVector = Icons.Default.Lock, contentDescription = null) },
                    onClick = onInvariantsClicked,
                )
            }
        }
    }
}

@Composable
private fun SettingsActionButton(
    text: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Text(text = text)
        }
    }
}
