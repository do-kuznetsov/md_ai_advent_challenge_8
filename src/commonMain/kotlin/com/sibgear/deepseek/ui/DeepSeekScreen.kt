package com.sibgear.deepseek.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

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
                ResponseArea(
                    text = state.output,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )

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
                        modifier = Modifier.height(56.dp),
                    ) {
                        Text(if (state.isLoading) "ждите" else "отправить")
                    }
                }

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
