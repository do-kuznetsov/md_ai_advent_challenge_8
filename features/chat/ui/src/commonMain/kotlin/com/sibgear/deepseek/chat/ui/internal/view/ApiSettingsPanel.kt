package com.sibgear.deepseek.chat.ui.internal.view

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.sibgear.deepseek.chat.domain.model.AiProvider
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
    val apiParamsScrollState = rememberScrollState()

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 12.dp)
                    .verticalScroll(apiParamsScrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ApiSettingsLabel(
                    text = "temperature: ${formatTemperature(state.apiSettings.temperature)}",
                    color = labelColor,
                )
                ApiSettingsHint(
                    text = TemperatureHint,
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
                ApiSettingsHint(
                    text = MaxTokensHint,
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
                ApiSettingsHint(
                    text = StopHint,
                    color = labelColor,
                )

                ShortcutOutlinedTextField(
                    value = state.apiSettings.stopWord,
                    onValueChange = { onEvent(ChatEvent.StopWordChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = isApiControlEnabled,
                )

                if (state.selectedModel.provider == AiProvider.Ollama) {
                    HorizontalDivider(modifier = Modifier.fillMaxWidth())

                    ApiSettingsLabel(
                        text = "Ollama options",
                        color = labelColor,
                    )

                    ApiSettingsLabel(
                        text = "num_ctx",
                        color = labelColor,
                    )
                    ApiSettingsHint(
                        text = NumCtxHint,
                        color = labelColor,
                    )
                    ShortcutOutlinedTextField(
                        value = state.numCtxInput,
                        onValueChange = { onEvent(ChatEvent.NumCtxChanged(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = isApiControlEnabled,
                    )

                    ApiSettingsLabel(
                        text = "top_p",
                        color = labelColor,
                    )
                    ApiSettingsHint(
                        text = TopPHint,
                        color = labelColor,
                    )
                    ShortcutOutlinedTextField(
                        value = state.topPInput,
                        onValueChange = { onEvent(ChatEvent.TopPChanged(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = isApiControlEnabled,
                    )

                    ApiSettingsLabel(
                        text = "seed",
                        color = labelColor,
                    )
                    ApiSettingsHint(
                        text = SeedHint,
                        color = labelColor,
                    )
                    ShortcutOutlinedTextField(
                        value = state.seedInput,
                        onValueChange = { onEvent(ChatEvent.SeedChanged(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = isApiControlEnabled,
                    )

                    ApiSettingsLabel(
                        text = "repeat_penalty",
                        color = labelColor,
                    )
                    ApiSettingsHint(
                        text = RepeatPenaltyHint,
                        color = labelColor,
                    )
                    ShortcutOutlinedTextField(
                        value = state.repeatPenaltyInput,
                        onValueChange = { onEvent(ChatEvent.RepeatPenaltyChanged(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = isApiControlEnabled,
                    )
                }
            }

            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(apiParamsScrollState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(8.dp),
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
private fun ApiSettingsHint(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        color = color.copy(alpha = 0.78f),
        style = MaterialTheme.typography.bodySmall,
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

private const val TemperatureHint =
    "Управляет случайностью ответа. Ниже — стабильнее и точнее, выше — разнообразнее, но больше риск ошибок."
private const val MaxTokensHint =
    "Максимальное число токенов, которое модель может сгенерировать в ответе. Малое значение может оборвать ответ."
private const val StopHint =
    "Строка, на которой генерация должна остановиться."
private const val NumCtxHint =
    "Размер контекстного окна. Чем больше, тем больше входных документов и истории модель может учитывать, но выше расход ресурсов."
private const val TopPHint =
    "Ограничивает выбор токенов наиболее вероятной массой. Ниже — консервативнее, выше — разнообразнее."
private const val SeedHint =
    "Фиксирует случайность генерации. При одинаковых параметрах помогает получать более воспроизводимые ответы."
private const val RepeatPenaltyHint =
    "Штраф за повторения. Значения выше 1 уменьшают повторяющиеся фразы, но слишком высокие могут ухудшить стиль."
