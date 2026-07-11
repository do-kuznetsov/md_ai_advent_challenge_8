package com.sibgear.deepseek.chat.ui.internal.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sibgear.deepseek.chat.ui.external.model.ChatEvent
import com.sibgear.deepseek.chat.ui.external.model.ChatViewState

@Composable
internal fun ApiSettingsPanel(
    state: ChatViewState,
    onEvent: (ChatEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isApiControlEnabled = state.apiSettings.isApiControlEnabled
    val labelColor = apiSettingsLabelColor(isApiControlEnabled)

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ApiSettingsLabel(
                text = "model",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ApiSettingsLabel(
                    text = "filter",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                ShortcutOutlinedTextField(
                    value = state.modelFilter,
                    onValueChange = { onEvent(ChatEvent.ModelFilterChanged(it)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }

            ModelSelector(
                state = state,
                onEvent = onEvent,
            )

            state.ollamaModelsStatus?.let { status ->
                ApiSettingsLabel(
                    text = status,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.openRouterModelsStatus?.let { status ->
                ApiSettingsLabel(
                    text = status,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider(modifier = Modifier.fillMaxWidth())

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = state.apiSettings.isApiControlEnabled,
                    onCheckedChange = { onEvent(ChatEvent.ApiControlChanged(it)) },
                )

                ApiSettingsLabel(
                    text = "API params",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ApiSettingsLabel(
                text = "temperature: ${formatTemperature(state.apiSettings.temperature)}",
                color = labelColor,
            )

            Slider(
                value = state.apiSettings.temperature,
                onValueChange = { onEvent(ChatEvent.TemperatureChanged(it)) },
                valueRange = 0f..1f,
                enabled = isApiControlEnabled,
            )

            ApiSettingsLabel(
                text = "max_tokens",
                color = labelColor,
            )

            ShortcutOutlinedTextField(
                value = state.maxTokensInput,
                onValueChange = { onEvent(ChatEvent.MaxTokensChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = isApiControlEnabled,
            )

            ApiSettingsLabel(
                text = "stop-слово",
                color = labelColor,
            )

            ShortcutOutlinedTextField(
                value = state.apiSettings.stopWord,
                onValueChange = { onEvent(ChatEvent.StopWordChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = isApiControlEnabled,
            )
        }
    }
}

@Composable
private fun ModelSelector(
    state: ChatViewState,
    onEvent: (ChatEvent) -> Unit,
) {
    Box {
        OutlinedButton(
            onClick = { onEvent(ChatEvent.ModelMenuExpandedChanged(true)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = state.selectedModel.displayName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        DropdownMenu(
            expanded = state.isModelMenuExpanded,
            onDismissRequest = {
                onEvent(ChatEvent.ModelMenuExpandedChanged(false))
            },
        ) {
            state.ollamaModels.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model.displayName) },
                    onClick = { onEvent(ChatEvent.ModelSelected(model)) },
                )
            }

            if (state.ollamaModels.isNotEmpty()) {
                HorizontalDivider()
            }

            state.openRouterModels.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model.displayName) },
                    onClick = { onEvent(ChatEvent.ModelSelected(model)) },
                )
            }

            if (state.openRouterModels.isNotEmpty()) {
                HorizontalDivider()
            }

            state.magnitCopilotModels.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model.displayName) },
                    onClick = { onEvent(ChatEvent.ModelSelected(model)) },
                )
            }

            if (state.magnitCopilotModels.isNotEmpty()) {
                HorizontalDivider()
            }

            state.deepSeekModels.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model.displayName) },
                    onClick = { onEvent(ChatEvent.ModelSelected(model)) },
                )
            }
        }
    }
}

@Composable
internal fun ApiSettingsLabel(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = color,
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
internal fun apiSettingsLabelColor(isEnabled: Boolean): Color {
    val baseColor = MaterialTheme.colorScheme.onSurfaceVariant
    return if (isEnabled) baseColor else baseColor.copy(alpha = 0.38f)
}

private fun formatTemperature(temperature: Float): String {
    val rounded = (temperature * 100).toInt() / 100f
    return rounded.toString()
}
