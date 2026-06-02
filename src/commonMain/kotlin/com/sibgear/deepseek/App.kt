package com.sibgear.deepseek

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val deepSeekModels = listOf(
    "deepseek-v4-flash",
    "deepseek-v4-pro",
    "deepseek-chat",
    "deepseek-reasoner",
)

@Composable
fun App() {
    val api = remember { DeepSeekApi() }
    val scope = rememberCoroutineScope()

    var prompt by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf(deepSeekModels.first()) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf("Ответ DeepSeek появится здесь.") }
    var isLoading by remember { mutableStateOf(false) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ResponseArea(
                    text = output,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        modifier = Modifier.weight(1f).height(96.dp),
                        minLines = 3,
                        maxLines = 3,
                        placeholder = { Text("Введите сообщение") },
                    )

                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                output = "Отправляю запрос..."
                                output = api.sendMessage(
                                    apiKey = apiKey,
                                    model = selectedModel,
                                    prompt = prompt,
                                )
                                isLoading = false
                            }
                        },
                        enabled = apiKey.isNotBlank() && !isLoading,
                        modifier = Modifier.height(56.dp),
                    ) {
                        Text(if (isLoading) "ждите" else "отправить")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("API key:")

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        placeholder = { Text("sk-...") },
                    )

                    Box {
                        OutlinedButton(onClick = { modelMenuExpanded = true }) {
                            Text(selectedModel)
                        }

                        DropdownMenu(
                            expanded = modelMenuExpanded,
                            onDismissRequest = { modelMenuExpanded = false },
                        ) {
                            deepSeekModels.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model) },
                                    onClick = {
                                        selectedModel = model
                                        modelMenuExpanded = false
                                    },
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
            adapter = androidx.compose.foundation.rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(8.dp),
        )
    }
}

private class DeepSeekApi {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val client = HttpClient {
        expectSuccess = false
        install(ContentNegotiation) {
            json(json)
        }
    }

    suspend fun sendMessage(apiKey: String, model: String, prompt: String): String {
        return try {
            val response = client.post("https://api.deepseek.com/chat/completions") {
                bearerAuth(apiKey)
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(
                    ChatCompletionRequest(
                        model = model,
                        messages = listOf(ChatMessage(role = "user", content = prompt)),
                        stream = false,
                        thinking = Thinking(type = "disabled"),
                    ),
                )
            }

            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                return formatApiError(response.status.value, response.status.description, body)
            }

            val completion = json.decodeFromString<ChatCompletionResponse>(body)
            completion.choices.firstOrNull()?.message?.content?.takeIf { it.isNotBlank() }
                ?: "DeepSeek вернул пустой ответ."
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Throwable) {
            "Ошибка запроса: ${exception.message ?: exception::class.simpleName ?: "unknown"}"
        }
    }

    private fun formatApiError(statusCode: Int, statusDescription: String, body: String): String {
        val apiMessage = runCatching {
            json.decodeFromString<DeepSeekErrorResponse>(body).error?.message
        }.getOrNull()

        val message = apiMessage ?: body.take(600).ifBlank { "без тела ответа" }
        return "Ошибка API: HTTP $statusCode $statusDescription\n$message"
    }
}

@Serializable
private data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean,
    val thinking: Thinking,
)

@Serializable
private data class ChatMessage(
    val role: String,
    val content: String,
)

@Serializable
private data class Thinking(
    val type: String,
)

@Serializable
private data class ChatCompletionResponse(
    val choices: List<Choice> = emptyList(),
)

@Serializable
private data class Choice(
    val message: AssistantMessage? = null,
)

@Serializable
private data class AssistantMessage(
    val content: String? = null,
)

@Serializable
private data class DeepSeekErrorResponse(
    val error: DeepSeekError? = null,
)

@Serializable
private data class DeepSeekError(
    val message: String? = null,
)
