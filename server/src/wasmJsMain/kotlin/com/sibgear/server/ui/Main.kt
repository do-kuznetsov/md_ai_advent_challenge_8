@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.sibgear.server.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import com.sibgear.server.protocol.ChatHistoryItem
import com.sibgear.server.protocol.ChatHistoryRole
import com.sibgear.server.protocol.ChatRequest
import com.sibgear.server.protocol.ChatStreamEvent
import com.sibgear.server.protocol.ServerApiSettings
import com.sibgear.server.protocol.ServerProtocolJson
import com.sibgear.server.protocol.ServerRagSettings
import com.sibgear.server.protocol.ServerRagStrategy
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.MessageEvent
import org.w3c.dom.WebSocket

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        ServerChatApp()
    }
}

@Composable
private fun ServerChatApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val messages = remember { mutableStateListOf<UiMessage>() }
            var prompt by remember { mutableStateOf("") }
            var isLoading by remember { mutableStateOf(false) }
            var status by remember { mutableStateOf<String?>(null) }
            var usedTokens by remember { mutableStateOf(0) }
            var maxTokens by remember { mutableStateOf(32768) }
            var apiSettings by remember { mutableStateOf(ServerApiSettings()) }
            var ragSettings by remember { mutableStateOf(ServerRagSettings()) }

            LaunchedEffect(Unit) {
                window.fetch("/api/config").then { response ->
                    response.text().then { text ->
                        val config = ServerProtocolJson.decodeFromString<com.sibgear.server.protocol.ServerPublicConfig>(
                            text.toString(),
                        )
                        maxTokens = config.contextSize
                        apiSettings = apiSettings.copy(numCtx = config.contextSize)
                        null
                    }
                    null
                }
            }

            Row(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ChatArea(
                        messages = messages,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )

                    ContextUsageBar(
                        usedTokens = usedTokens,
                        maxTokens = maxTokens,
                        status = status,
                    )

                    PromptInput(
                        prompt = prompt,
                        isLoading = isLoading,
                        onPromptChanged = { prompt = it },
                        onSend = {
                            val trimmed = prompt.trim()
                            if (trimmed.isEmpty() || isLoading) {
                                return@PromptInput
                            }
                            val assistant = UiMessage(ChatHistoryRole.Assistant, "", "")
                            messages += UiMessage(ChatHistoryRole.User, trimmed)
                            messages += assistant
                            prompt = ""
                            isLoading = true
                            status = null
                            sendChatRequest(
                                request = ChatRequest(
                                    prompt = trimmed,
                                    history = messages
                                        .dropLast(2)
                                        .filter { it.content.isNotBlank() }
                                        .map { ChatHistoryItem(it.role, it.content) },
                                    apiSettings = apiSettings,
                                    ragSettings = ragSettings,
                                ),
                                onEvent = { event ->
                                    when (event) {
                                        is ChatStreamEvent.Context -> {
                                            usedTokens = event.usedTokens
                                            maxTokens = event.maxTokens
                                        }
                                        is ChatStreamEvent.RagStatus -> status = event.message
                                        is ChatStreamEvent.ThinkingDelta -> {
                                            assistant.thinking += event.text
                                        }
                                        is ChatStreamEvent.ContentDelta -> {
                                            assistant.content += event.text
                                        }
                                        is ChatStreamEvent.Done -> {
                                            assistant.content = event.content
                                            assistant.thinking = event.thinking
                                            usedTokens = event.usedTokens
                                            maxTokens = event.maxTokens
                                            isLoading = false
                                        }
                                        is ChatStreamEvent.Error -> {
                                            assistant.content = "Ошибка: ${event.message}"
                                            isLoading = false
                                        }
                                    }
                                },
                                onClosed = { isLoading = false },
                            )
                        },
                    )
                }

                SettingsPanel(
                    apiSettings = apiSettings,
                    ragSettings = ragSettings,
                    onApiSettingsChanged = { apiSettings = it },
                    onRagSettingsChanged = { ragSettings = it },
                    modifier = Modifier.width(360.dp).fillMaxHeight(),
                )
            }
        }
    }
}

private fun sendChatRequest(
    request: ChatRequest,
    onEvent: (ChatStreamEvent) -> Unit,
    onClosed: () -> Unit,
) {
    val protocol = if (window.location.protocol == "https:") "wss" else "ws"
    val socket = WebSocket("$protocol://${window.location.host}/api/chat")
    socket.onopen = {
        socket.send(ServerProtocolJson.encodeToString(ChatRequest.serializer(), request))
    }
    socket.onmessage = { event: MessageEvent ->
        val text = event.data.toString()
        if (text.isNotBlank()) {
            onEvent(ServerProtocolJson.decodeFromString(ChatStreamEvent.serializer(), text))
        }
    }
    socket.onerror = {
        onEvent(ChatStreamEvent.Error("WebSocket error."))
    }
    socket.onclose = {
        onClosed()
    }
}

@Composable
private fun ChatArea(
    messages: List<UiMessage>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(messages) { message ->
            ChatBubble(message)
        }
    }
}

@Composable
private fun ChatBubble(message: UiMessage) {
    val isUser = message.role == ChatHistoryRole.User
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.Start else Arrangement.End,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .background(
                    if (isUser) Color(0xFFDDF7DF) else Color(0xFFEDE1FF),
                    RoundedCornerShape(8.dp),
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (message.thinking.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE7E0EC), RoundedCornerShape(6.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "thinking",
                        color = Color(0xFF5F6368),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = message.thinking,
                        color = Color(0xFF5F6368),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Text(
                text = message.content,
                color = Color(0xFF202124),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ContextUsageBar(
    usedTokens: Int,
    maxTokens: Int,
    status: String?,
) {
    val fraction = if (maxTokens <= 0) 0f else (usedTokens.toFloat() / maxTokens).coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "context: $usedTokens / $maxTokens${status?.let { " · $it" } ?: ""}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PromptInput(
    prompt: String,
    isLoading: Boolean,
    onPromptChanged: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChanged,
            modifier = Modifier.weight(1f).height(132.dp),
            minLines = 3,
            maxLines = 4,
            enabled = !isLoading,
            placeholder = { Text("Введите сообщение") },
        )
        IconButton(
            onClick = onSend,
            enabled = prompt.isNotBlank() && !isLoading,
            modifier = Modifier
                .width(52.dp)
                .height(52.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(26.dp)),
        ) {
            Icon(
                imageVector = Icons.Default.ArrowUpward,
                contentDescription = "send",
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun SettingsPanel(
    apiSettings: ServerApiSettings,
    ragSettings: ServerRagSettings,
    onApiSettingsChanged: (ServerApiSettings) -> Unit,
    onRagSettingsChanged: (ServerRagSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("AI модель", style = MaterialTheme.typography.titleSmall)
        Text("temperature: ${apiSettings.temperature}", style = MaterialTheme.typography.labelSmall)
        Slider(
            value = apiSettings.temperature,
            onValueChange = { onApiSettingsChanged(apiSettings.copy(temperature = it)) },
            valueRange = 0f..1f,
        )
        NumberField("max_tokens", apiSettings.maxTokens.toString()) {
            onApiSettingsChanged(apiSettings.copy(maxTokens = it.toIntOrNull() ?: apiSettings.maxTokens))
        }
        NumberField("num_ctx", apiSettings.numCtx.toString()) {
            onApiSettingsChanged(apiSettings.copy(numCtx = it.toIntOrNull() ?: apiSettings.numCtx))
        }
        NumberField("top_p", apiSettings.topP.toString()) {
            onApiSettingsChanged(apiSettings.copy(topP = it.toFloatOrNull() ?: apiSettings.topP))
        }
        NumberField("seed", apiSettings.seed.toString()) {
            onApiSettingsChanged(apiSettings.copy(seed = it.toIntOrNull() ?: apiSettings.seed))
        }
        NumberField("repeat_penalty", apiSettings.repeatPenalty.toString()) {
            onApiSettingsChanged(apiSettings.copy(repeatPenalty = it.toFloatOrNull() ?: apiSettings.repeatPenalty))
        }
        OutlinedTextField(
            value = apiSettings.stopWord,
            onValueChange = { onApiSettingsChanged(apiSettings.copy(stopWord = it)) },
            label = { Text("stop-слово") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        HorizontalDivider()

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = ragSettings.isEnabled,
                onCheckedChange = { onRagSettingsChanged(ragSettings.copy(isEnabled = it)) },
            )
            Text("RAG", style = MaterialTheme.typography.titleSmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StrategyButton(
                text = "fixed",
                selected = ragSettings.strategy == ServerRagStrategy.Fixed,
                enabled = ragSettings.isEnabled,
                onClick = { onRagSettingsChanged(ragSettings.copy(strategy = ServerRagStrategy.Fixed)) },
                modifier = Modifier.weight(1f),
            )
            StrategyButton(
                text = "structure",
                selected = ragSettings.strategy == ServerRagStrategy.Structure,
                enabled = ragSettings.isEnabled,
                onClick = { onRagSettingsChanged(ragSettings.copy(strategy = ServerRagStrategy.Structure)) },
                modifier = Modifier.weight(1f),
            )
        }
        Toggle("rewrite", ragSettings.isQueryRewriteEnabled, ragSettings.isEnabled) {
            onRagSettingsChanged(ragSettings.copy(isQueryRewriteEnabled = it))
        }
        Toggle("filter", ragSettings.isFilteringEnabled, ragSettings.isEnabled) {
            onRagSettingsChanged(ragSettings.copy(isFilteringEnabled = it))
        }
        Toggle("rerank", ragSettings.isRerankingEnabled, ragSettings.isEnabled) {
            onRagSettingsChanged(ragSettings.copy(isRerankingEnabled = it))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("before", ragSettings.topKBeforeFilter.toString(), Modifier.weight(1f)) {
                onRagSettingsChanged(ragSettings.copy(topKBeforeFilter = it.toIntOrNull() ?: ragSettings.topKBeforeFilter))
            }
            NumberField("after", ragSettings.topKAfterFilter.toString(), Modifier.weight(1f)) {
                onRagSettingsChanged(ragSettings.copy(topKAfterFilter = it.toIntOrNull() ?: ragSettings.topKAfterFilter))
            }
            NumberField("threshold", ragSettings.similarityThreshold.toString(), Modifier.weight(1f)) {
                onRagSettingsChanged(
                    ragSettings.copy(similarityThreshold = it.toFloatOrNull() ?: ragSettings.similarityThreshold),
                )
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
    )
}

@Composable
private fun Toggle(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
        Text(label)
    }
}

@Composable
private fun StrategyButton(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(onClick = onClick, enabled = enabled, modifier = modifier) {
            Text(text)
        }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) {
            Text(text)
        }
    }
}

private class UiMessage(
    val role: ChatHistoryRole,
    content: String,
    thinking: String = "",
) {
    var content by mutableStateOf(content)
    var thinking by mutableStateOf(thinking)
}
