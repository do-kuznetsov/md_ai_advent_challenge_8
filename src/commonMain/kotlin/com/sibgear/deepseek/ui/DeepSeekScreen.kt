package com.sibgear.deepseek.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val SendButtonWidth = 116.dp

@Composable
fun DeepSeekScreen(
    state: DeepSeekViewState,
    onEvent: (DeepSeekViewEvent) -> Unit,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                )
                {
                    ResponseArea(
                        text = state.output,
                        modifier = Modifier.weight(0.8f).fillMaxHeight(),
                    )

                    ApiSettingsPanel(
                        state = state,
                        onEvent = onEvent,
                        modifier = Modifier.weight(0.2f).fillMaxHeight(),
                    )
                }

                PromptInputArea(
                    state = state,
                    onEvent = onEvent,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("API key:")

                    OutlinedTextField(
                        value = state.apiKey,
                        onValueChange = { onEvent(DeepSeekViewEvent.ApiKeyChanged(it)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        placeholder = { Text("sk-...") },
                    )

                    Box {
                        OutlinedButton(
                            onClick = { onEvent(DeepSeekViewEvent.ModelMenuExpandedChanged(true)) },
                        ) {
                            Text(state.selectedModel.id)
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
            }
        }
    }
}

@Composable
private fun PromptInputArea(
    state: DeepSeekViewState,
    onEvent: (DeepSeekViewEvent) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("system prompt:")

            OutlinedTextField(
                value = state.systemPrompt,
                onValueChange = { onEvent(DeepSeekViewEvent.SystemPromptChanged(it)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )

            Spacer(modifier = Modifier.width(SendButtonWidth))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = state.prompt,
                onValueChange = { onEvent(DeepSeekViewEvent.PromptChanged(it)) },
                modifier = Modifier.weight(1f).height(96.dp),
                minLines = 3,
                maxLines = 3,
                placeholder = { Text("Введите сообщение") },
            )

            Button(
                onClick = { onEvent(DeepSeekViewEvent.SendClicked) },
                enabled = state.isSendEnabled,
                modifier = Modifier.width(SendButtonWidth).height(56.dp),
            ) {
                Text(if (state.isLoading) "ждите" else "отправить")
            }
        }
    }
}

@Composable
private fun ApiSettingsPanel(
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

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Checkbox(
                checked = state.apiSettings.isApiControlEnabled,
                onCheckedChange = { onEvent(DeepSeekViewEvent.ApiControlChanged(it)) },
            )

            ApiSettingsLabel(
                text = "API control",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

@Composable
private fun ResponseArea(text: String, modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
    ) {
        SelectionContainer {
            Text(
                text = text,
                modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(8.dp),
        )
    }
}

private fun formatTemperature(temperature: Float): String {
    val rounded = (temperature * 100).toInt() / 100f
    return rounded.toString()
}
