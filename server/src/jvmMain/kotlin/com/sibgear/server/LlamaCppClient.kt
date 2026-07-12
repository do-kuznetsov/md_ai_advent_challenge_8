package com.sibgear.server

import com.sibgear.server.protocol.ChatHistoryRole
import com.sibgear.server.protocol.ChatStreamEvent
import com.sibgear.server.protocol.ServerApiSettings
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readLine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray

internal val ServerJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

class LlamaCppClient(
    private val baseUrl: String,
    private val modelId: String,
    private val client: HttpClient = defaultHttpClient(),
) {
    private val apiUrl = baseUrl.trimEnd('/')

    suspend fun completeOnce(
        messages: List<LlamaChatMessage>,
        settings: ServerApiSettings,
    ): String {
        val response = client.post("$apiUrl/v1/chat/completions") {
            contentType(ContentType.Application.Json)
            setBody(
                LlamaChatCompletionRequest(
                    model = modelId,
                    messages = messages,
                    stream = false,
                    temperature = settings.temperature,
                    maxTokens = settings.maxTokens,
                    topP = settings.topP,
                    seed = settings.seed,
                    repeatPenalty = settings.repeatPenalty,
                    stop = settings.stopWords(),
                ),
            )
        }
        if (!response.status.isSuccess()) {
            error("llama.cpp HTTP ${response.status.value}: ${response.bodyAsText().take(400)}")
        }
        return response.body<LlamaChatCompletionResponse>()
            .choices
            .firstOrNull()
            ?.message
            ?.content
            .orEmpty()
    }

    suspend fun streamChat(
        messages: List<LlamaChatMessage>,
        settings: ServerApiSettings,
        emit: suspend (ChatStreamEvent) -> Unit,
    ): LlamaCompletion {
        val parser = LlamaStreamParser()
        return client.preparePost("$apiUrl/v1/chat/completions") {
            contentType(ContentType.Application.Json)
            setBody(
                LlamaChatCompletionRequest(
                    model = modelId,
                    messages = messages,
                    stream = true,
                    temperature = settings.temperature,
                    maxTokens = settings.maxTokens,
                    topP = settings.topP,
                    seed = settings.seed,
                    repeatPenalty = settings.repeatPenalty,
                    stop = settings.stopWords(),
                ),
            )
            timeout {
                connectTimeoutMillis = 30_000
                requestTimeoutMillis = 0
                socketTimeoutMillis = 0
            }
        }.execute { response ->
            if (!response.status.isSuccess()) {
                error("llama.cpp HTTP ${response.status.value}: ${response.bodyAsText().take(400)}")
            }
            val channel = response.bodyAsChannel()
            while (true) {
                val line = channel.readLine() ?: break
                val events = mutableListOf<ChatStreamEvent>()
                parser.acceptSseLine(line, events::add)
                events.forEach { event -> emit(event) }
            }
            parser.result()
        }
    }

    suspend fun countTokens(text: String): Int {
        if (text.isBlank()) {
            return 0
        }
        return runCatching {
            val response = client.post("$apiUrl/tokenize") {
                contentType(ContentType.Application.Json)
                setBody(TokenizeRequest(content = text))
            }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                error(body)
            }
            val root = ServerJson.decodeFromString<JsonObject>(body)
            val tokens = root["tokens"] as? JsonArray ?: root["content"] as? JsonArray
            tokens?.jsonArray?.size ?: error("tokens field not found")
        }.getOrElse {
            estimateTokens(text)
        }
    }

    private fun estimateTokens(text: String): Int =
        (text.length / 4).coerceAtLeast(1)
}

@Serializable
data class LlamaChatMessage(
    val role: String,
    val content: String,
)

fun ChatHistoryRole.toLlamaRole(): String =
    when (this) {
        ChatHistoryRole.User -> "user"
        ChatHistoryRole.Assistant -> "assistant"
    }

@Serializable
private data class LlamaChatCompletionRequest(
    val model: String,
    val messages: List<LlamaChatMessage>,
    val stream: Boolean,
    val temperature: Float? = null,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    @SerialName("top_p")
    val topP: Float? = null,
    val seed: Int? = null,
    @SerialName("repeat_penalty")
    val repeatPenalty: Float? = null,
    val stop: List<String>? = null,
)

@Serializable
private data class LlamaChatCompletionResponse(
    val choices: List<LlamaChatChoice> = emptyList(),
)

@Serializable
private data class LlamaChatChoice(
    val message: LlamaChatMessage? = null,
)

@Serializable
private data class TokenizeRequest(
    val content: String,
)

private fun ServerApiSettings.stopWords(): List<String>? =
    stopWord
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toList()
        .takeIf { it.isNotEmpty() }

private fun defaultHttpClient(): HttpClient =
    HttpClient(io.ktor.client.engine.cio.CIO) {
        install(ContentNegotiation) {
            json(ServerJson)
        }
    }
