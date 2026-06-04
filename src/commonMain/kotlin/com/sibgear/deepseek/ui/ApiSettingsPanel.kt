package com.sibgear.deepseek.ui

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ApiSettingsPanel(
    state: DeepSeekViewState,
    onEvent: (DeepSeekViewEvent) -> Unit,
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

            ModelSelector(
                state = state,
                onEvent = onEvent,
            )

            HorizontalDivider(modifier = Modifier.fillMaxWidth())

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = state.apiSettings.isApiControlEnabled,
                    onCheckedChange = { onEvent(DeepSeekViewEvent.ApiControlChanged(it)) },
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
                onValueChange = { onEvent(DeepSeekViewEvent.TemperatureChanged(it)) },
                valueRange = 0f..1f,
                enabled = isApiControlEnabled,
            )

            ApiSettingsLabel(
                text = "max_tokens",
                color = labelColor,
            )

            OutlinedTextField(
                value = state.maxTokensInput,
                onValueChange = { onEvent(DeepSeekViewEvent.MaxTokensChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = isApiControlEnabled,
            )

            ApiSettingsLabel(
                text = "stop-слово",
                color = labelColor,
            )

            OutlinedTextField(
                value = state.apiSettings.stopWord,
                onValueChange = { onEvent(DeepSeekViewEvent.StopWordChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = isApiControlEnabled,
            )
        }
    }
}

@Composable
private fun ModelSelector(
    state: DeepSeekViewState,
    onEvent: (DeepSeekViewEvent) -> Unit,
) {
    Box {
        OutlinedButton(
            onClick = { onEvent(DeepSeekViewEvent.ModelMenuExpandedChanged(true)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = state.selectedModel.id,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        DropdownMenu(
            expanded = state.isModelMenuExpanded,
            onDismissRequest = {
                onEvent(DeepSeekViewEvent.ModelMenuExpandedChanged(false))
            },
        ) {
            state.availableModels.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model.id) },
                    onClick = { onEvent(DeepSeekViewEvent.ModelSelected(model)) },
                )
            }
        }
    }
}

@Composable
private fun ApiSettingsLabel(
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
private fun apiSettingsLabelColor(isEnabled: Boolean): Color {
    val baseColor = MaterialTheme.colorScheme.onSurfaceVariant
    return if (isEnabled) baseColor else baseColor.copy(alpha = 0.38f)
}

private fun formatTemperature(temperature: Float): String {
    val rounded = (temperature * 100).toInt() / 100f
    return rounded.toString()
}
