package com.sibgear.deepseek.chat.workspace.ui.internal.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
internal fun UserProfileDialog(
    profileDraft: String,
    isActionEnabled: Boolean,
    isInterviewActive: Boolean,
    interviewQuestionIndex: Int,
    interviewAnswerInput: String,
    isInterviewLoading: Boolean,
    error: String?,
    onDismissRequest: () -> Unit,
    onProfileChanged: (String) -> Unit,
    onSaveClicked: () -> Unit,
    onInterviewClicked: () -> Unit,
    onInterviewAnswerChanged: (String) -> Unit,
    onInterviewAnswerSubmitted: () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier.widthIn(min = 640.dp, max = 860.dp),
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Профиль пользователя",
                    style = MaterialTheme.typography.titleMedium,
                )

                OutlinedTextField(
                    value = profileDraft,
                    onValueChange = onProfileChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 280.dp),
                    enabled = isActionEnabled,
                    minLines = 8,
                    maxLines = 12,
                )

                if (isInterviewActive || isInterviewLoading) {
                    InterviewBlock(
                        questionIndex = interviewQuestionIndex,
                        answerInput = interviewAnswerInput,
                        isLoading = isInterviewLoading,
                        isActionEnabled = isActionEnabled,
                        onAnswerChanged = onInterviewAnswerChanged,
                        onAnswerSubmitted = onInterviewAnswerSubmitted,
                    )
                }

                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onInterviewClicked,
                        enabled = isActionEnabled,
                    ) {
                        Text("интервью")
                    }

                    Button(
                        onClick = onSaveClicked,
                        enabled = isActionEnabled,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text("сохранить")
                    }
                }
            }
        }
    }
}

@Composable
private fun InterviewBlock(
    questionIndex: Int,
    answerInput: String,
    isLoading: Boolean,
    isActionEnabled: Boolean,
    onAnswerChanged: (String) -> Unit,
    onAnswerSubmitted: () -> Unit,
) {
    val safeIndex = questionIndex.coerceIn(0, ProfileQuestions.lastIndex)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Вопрос ${safeIndex + 1}/${ProfileQuestions.size}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )

        Text(
            text = ProfileQuestions[safeIndex],
            style = MaterialTheme.typography.bodyMedium,
        )

        if (isLoading) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    text = "обновляю профиль",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = answerInput,
                    onValueChange = onAnswerChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp, max = 180.dp),
                    enabled = isActionEnabled,
                    minLines = 3,
                    maxLines = 6,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onAnswerSubmitted,
                        enabled = isActionEnabled,
                    ) {
                        Text("ответить")
                    }
                }
            }
        }
    }
}

private val ProfileQuestions = listOf(
    "На каком языке с тобой общаться и как обращаться? Если удобно, укажи имя/ник и основной язык.",
    "Какие ответы тебе комфортнее: короткие или подробные? Нужны ли объяснения шагов, примеры, альтернативы?",
    "Какой стиль общения предпочитаешь: формальный, спокойный рабочий, разговорный? Что точно раздражает?",
    "Кто ты на проекте и что это за проект? Кратко опиши роль, продукт, аудиторию и текущую цель.",
    "Какие технические рамки важно учитывать: стек, архитектура, ограничения по зависимостям, кодстайлу, тестам, безопасности или процессу?",
)
