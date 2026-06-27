package com.sibgear.mcp.client

import com.sibgear.deepseek.chat.domain.model.AiToolCatalog
import com.sibgear.deepseek.chat.domain.model.AiToolDefinition
import com.sibgear.deepseek.chat.domain.model.AiToolInvocation
import com.sibgear.deepseek.chat.domain.model.AiToolProvider
import com.sibgear.deepseek.chat.domain.model.AiToolResult
import com.sibgear.deepseek.chat.domain.model.AiToolSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class McpServerConnection(
    val id: Int,
    val name: String,
    val url: String,
    val isEnabled: Boolean,
)

class McpAiToolProvider internal constructor(
    private val loadServers: () -> List<McpServerConnection>,
    private val sessionFactory: McpRemoteSessionFactory,
) : AiToolProvider {
    constructor(
        loadServers: () -> List<McpServerConnection>,
    ) : this(
        loadServers = loadServers,
        sessionFactory = DefaultMcpRemoteSessionFactory(),
    )

    override suspend fun openSession(): AiToolSession =
        connectSession()

    private suspend fun connectSession(): McpAiToolSession {
        val sessions = mutableListOf<McpConnectedSession>()
        val tools = mutableListOf<AiToolDefinition>()
        val warnings = mutableListOf<String>()
        val usedNames = mutableSetOf<String>()
        val mappings = mutableMapOf<String, McpToolMapping>()

        loadServers()
            .filter { it.isEnabled }
            .forEach { server ->
                try {
                    val remoteSession = sessionFactory.connect(server)
                    val connected = McpConnectedSession(server = server, session = remoteSession)
                    sessions += connected
                    remoteSession.tools.forEach { tool ->
                        val exposedName = uniqueToolName(
                            baseName = "${server.name.sanitizedToolPart()}__${tool.name.sanitizedToolPart()}",
                            usedNames = usedNames,
                        )
                        mappings[exposedName] = McpToolMapping(
                            exposedName = exposedName,
                            toolName = tool.name,
                            connectedSession = connected,
                        )
                        tools += tool.toAiToolDefinition(exposedName)
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Throwable) {
                    warnings += "MCP сервер '${server.name}' недоступен: ${exception.diagnosticMessage()}"
                }
            }

        return McpAiToolSession(
            sessions = sessions,
            tools = tools,
            warnings = warnings,
            mappings = mappings,
        )
    }
}

internal class McpAiToolSession(
    val tools: List<AiToolDefinition>,
    val warnings: List<String>,
    private val sessions: List<McpConnectedSession>,
    private val mappings: Map<String, McpToolMapping>,
) : AiToolSession {
    override val catalog: AiToolCatalog =
        AiToolCatalog(
            tools = tools,
            warnings = warnings,
        )

    override suspend fun callTool(invocation: AiToolInvocation): AiToolResult {
        val mapping = mappings[invocation.name]
            ?: return AiToolResult(
                name = invocation.name,
                content = "MCP tool '${invocation.name}' не найден.",
                isError = true,
            )

        return try {
            val result = mapping.connectedSession.session.callTool(
                name = mapping.toolName,
                arguments = invocation.arguments,
            )
            AiToolResult(
                name = invocation.name,
                content = if (result.isError) {
                    "Ошибка MCP tool '${invocation.name}':\n${result.content}"
                } else {
                    result.content
                },
                isError = result.isError,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Throwable) {
            AiToolResult(
                name = invocation.name,
                content = "Ошибка MCP tool '${invocation.name}': ${exception.diagnosticMessage()}",
                isError = true,
            )
        }
    }

    override suspend fun close() {
        sessions.forEach { connected ->
            runCatching { connected.session.close() }
        }
    }
}

internal interface McpRemoteSessionFactory {
    suspend fun connect(server: McpServerConnection): McpRemoteSession
}

internal class DefaultMcpRemoteSessionFactory(
    private val httpClient: HttpClient = HttpClient(CIO) {
        install(SSE)
    },
    private val primaryDiscoveryTimeout: Duration = 5.seconds,
) : McpRemoteSessionFactory {
    override suspend fun connect(server: McpServerConnection): McpRemoteSession {
        val primaryError = runCatching {
            withTimeout(primaryDiscoveryTimeout) {
                connectWithSdk(server)
            }
        }.fold(
            onSuccess = { return it },
            onFailure = { error ->
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                error
            },
        )

        return runCatching {
            PostOnlyJsonRpcMcpClient(
                httpClient = httpClient,
                url = server.url,
            ).connect()
        }.getOrElse { fallbackError ->
            if (fallbackError is CancellationException && fallbackError !is TimeoutCancellationException) throw fallbackError
            throw IllegalStateException(
                "Streamable HTTP failed: ${primaryError.diagnosticMessage()}; " +
                    "POST-only fallback failed: ${fallbackError.diagnosticMessage()}",
                fallbackError,
            )
        }
    }

    private suspend fun connectWithSdk(server: McpServerConnection): McpRemoteSession {
        val client = Client(
            clientInfo = Implementation(
                name = "deepseek-client",
                version = "1.0.0",
            ),
        )
        return try {
            val transport = StreamableHttpClientTransport(
                client = httpClient,
                url = server.url,
            )
            client.connect(transport)
            SdkMcpRemoteSession(
                client = client,
                tools = client.listTools().tools.map { it.toRemoteMcpTool() },
            )
        } catch (throwable: Throwable) {
            runCatching { client.close() }
            throw throwable
        }
    }
}

internal interface McpRemoteSession {
    val tools: List<RemoteMcpTool>
    suspend fun callTool(name: String, arguments: JsonObject): RemoteMcpToolResult
    suspend fun close()
}

internal data class RemoteMcpTool(
    val name: String,
    val description: String?,
    val inputSchema: JsonObject,
)

internal data class RemoteMcpToolResult(
    val content: String,
    val isError: Boolean,
)

private class SdkMcpRemoteSession(
    private val client: Client,
    override val tools: List<RemoteMcpTool>,
) : McpRemoteSession {
    override suspend fun callTool(name: String, arguments: JsonObject): RemoteMcpToolResult {
        val result = client.callTool(
            name = name,
            arguments = arguments.toAnyMap(),
        )
        return RemoteMcpToolResult(
            content = result.textContent(),
            isError = result.isError == true,
        )
    }

    override suspend fun close() {
        client.close()
    }
}

private class PostOnlyJsonRpcMcpClient(
    private val httpClient: HttpClient,
    private val url: String,
) {
    private var nextRequestId = 1
    private var protocolVersion: String? = null
    private var sessionId: String? = null

    suspend fun connect(): McpRemoteSession {
        val initialize = request(
            method = "initialize",
            params = buildJsonObject {
                put("protocolVersion", LatestMcpProtocolVersion)
                put("capabilities", JsonObject(emptyMap()))
                put(
                    "clientInfo",
                    buildJsonObject {
                        put("name", "deepseek-client")
                        put("version", "1.0.0")
                    },
                )
            },
            sendSessionHeaders = false,
        )
        protocolVersion = initialize.jsonObjectOrNull("result")
            ?.jsonPrimitiveString("protocolVersion")
            ?: LatestMcpProtocolVersion

        runCatching {
            notification(
                method = "notifications/initialized",
                params = JsonObject(emptyMap()),
            )
        }

        val listTools = request(
            method = "tools/list",
            params = JsonObject(emptyMap()),
        )
        val tools = listTools.jsonObjectOrNull("result")
            ?.jsonArrayOrNull("tools")
            ?.mapNotNull { it.jsonObjectOrNull()?.toRemoteMcpTool() }
            ?: emptyList()

        return PostOnlyMcpRemoteSession(
            client = this,
            tools = tools,
        )
    }

    suspend fun callTool(name: String, arguments: JsonObject): RemoteMcpToolResult {
        val response = request(
            method = "tools/call",
            params = buildJsonObject {
                put("name", name)
                put("arguments", arguments)
            },
            throwJsonRpcErrors = false,
        )
        response.jsonObjectOrNull("error")?.let { error ->
            return RemoteMcpToolResult(
                content = error.jsonPrimitiveString("message")
                    ?: "MCP JSON-RPC error: ${json.encodeToString(error)}",
                isError = true,
            )
        }

        val result = response.jsonObjectOrNull("result") ?: JsonObject(emptyMap())
        return RemoteMcpToolResult(
            content = result.toolResultContent(),
            isError = result.jsonPrimitiveBoolean("isError") == true,
        )
    }

    private suspend fun request(
        method: String,
        params: JsonObject,
        sendSessionHeaders: Boolean = true,
        throwJsonRpcErrors: Boolean = true,
    ): JsonObject {
        val response = postJsonRpc(
            body = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", nextRequestId++)
                put("method", method)
                put("params", params)
            },
            sendSessionHeaders = sendSessionHeaders,
        )
        val payload = response.readJsonObject()
        payload.jsonObjectOrNull("error")?.takeIf { throwJsonRpcErrors }?.let { error ->
            throw IllegalStateException(
                "MCP JSON-RPC error for '$method': " +
                    (error.jsonPrimitiveString("message") ?: json.encodeToString(error)),
            )
        }
        if (payload["id"] == null || (payload["jsonrpc"] as? JsonPrimitive)?.contentOrNull != "2.0") {
            throw IllegalStateException("Invalid JSON-RPC response for '$method': ${json.encodeToString(payload)}")
        }
        return payload
    }

    private suspend fun notification(method: String, params: JsonObject) {
        postJsonRpc(
            body = buildJsonObject {
                put("jsonrpc", "2.0")
                put("method", method)
                put("params", params)
            },
        )
    }

    private suspend fun postJsonRpc(
        body: JsonObject,
        sendSessionHeaders: Boolean = true,
    ): HttpResponse {
        val response = httpClient.post(url) {
            accept(ContentType.Application.Json)
            accept(ContentType.Text.EventStream)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            if (sendSessionHeaders) {
                protocolVersion?.let { header(McpProtocolVersionHeader, it) }
                sessionId?.let { header(McpSessionIdHeader, it) }
            }
            setBody(json.encodeToString(body))
        }
        response.headers[McpSessionIdHeader]?.let { sessionId = it }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("HTTP ${response.status.value}: ${response.bodyAsText()}")
        }
        return response
    }
}

private class PostOnlyMcpRemoteSession(
    private val client: PostOnlyJsonRpcMcpClient,
    override val tools: List<RemoteMcpTool>,
) : McpRemoteSession {
    override suspend fun callTool(name: String, arguments: JsonObject): RemoteMcpToolResult =
        client.callTool(name = name, arguments = arguments)

    override suspend fun close() = Unit
}

internal data class McpConnectedSession(
    val server: McpServerConnection,
    val session: McpRemoteSession,
)

internal data class McpToolMapping(
    val exposedName: String,
    val toolName: String,
    val connectedSession: McpConnectedSession,
)

private fun Tool.toRemoteMcpTool(): RemoteMcpTool =
    RemoteMcpTool(
        name = name,
        description = description,
        inputSchema = JsonObject(
            buildMap {
                put("type", JsonPrimitive(inputSchema.type))
                inputSchema.schema?.let { put("\$schema", JsonPrimitive(it)) }
                inputSchema.properties?.let { put("properties", it) }
                inputSchema.required?.let { required ->
                    put("required", JsonArray(required.map(::JsonPrimitive)))
                }
                inputSchema.defs?.let { put("\$defs", it) }
            },
        ),
    )

private fun JsonObject.toRemoteMcpTool(): RemoteMcpTool? {
    val name = jsonPrimitiveString("name") ?: return null
    return RemoteMcpTool(
        name = name,
        description = jsonPrimitiveString("description"),
        inputSchema = jsonObjectOrNull("inputSchema")
            ?: buildJsonObject { put("type", "object") },
    )
}

private fun RemoteMcpTool.toAiToolDefinition(exposedName: String): AiToolDefinition =
    AiToolDefinition(
        name = exposedName,
        description = description,
        parameters = inputSchema,
    )

private fun CallToolResult.textContent(): String =
    content.joinToString(separator = "\n") { content ->
        when (content) {
            is TextContent -> content.text
            else -> content.toString()
        }
    }.ifBlank {
        if (isError == true) {
            "MCP tool вернул ошибку без текста."
        } else {
            "MCP tool вернул пустой ответ."
        }
    }

private fun JsonObject.toolResultContent(): String =
    jsonArrayOrNull("content")
        ?.joinToString(separator = "\n") { content ->
            val block = content.jsonObjectOrNull()
            if (block?.jsonPrimitiveString("type") == "text") {
                block.jsonPrimitiveString("text").orEmpty()
            } else {
                json.encodeToString(content)
            }
        }
        ?.ifBlank {
            if (jsonPrimitiveBoolean("isError") == true) {
                "MCP tool вернул ошибку без текста."
            } else {
                "MCP tool вернул пустой ответ."
            }
        }
        ?: json.encodeToString(this)

private suspend fun HttpResponse.readJsonObject(): JsonObject {
    val contentType = contentType()?.withoutParameters()
    val body = bodyAsText()
    if (contentType != null && contentType != ContentType.Application.Json) {
        throw IllegalStateException("Unexpected content type: $contentType")
    }
    return runCatching { json.decodeFromString<JsonObject>(body) }
        .getOrElse { throw IllegalStateException("Invalid JSON body: $body", it) }
}

private fun JsonElement.jsonObjectOrNull(): JsonObject? =
    this as? JsonObject

private fun JsonObject.jsonObjectOrNull(key: String): JsonObject? =
    this[key] as? JsonObject

private fun JsonObject.jsonArrayOrNull(key: String): JsonArray? =
    this[key] as? JsonArray

private fun JsonObject.jsonPrimitiveString(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.jsonPrimitiveBoolean(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.booleanOrNull

private fun JsonObject.toAnyMap(): Map<String, Any?> =
    mapValues { (_, value) -> value.toAnyValue() }

private fun JsonElement.toAnyValue(): Any? =
    when (this) {
        JsonNull -> null
        is JsonObject -> toAnyMap()
        is JsonArray -> map { it.toAnyValue() }
        is JsonPrimitive -> when {
            isString -> content
            booleanOrNull != null -> booleanOrNull
            intOrNull != null -> intOrNull
            doubleOrNull != null -> doubleOrNull
            else -> content
        }
    }

private fun uniqueToolName(baseName: String, usedNames: MutableSet<String>): String {
    var candidate = baseName.ifBlank { "mcp_tool" }.take(OpenAiToolNameMaxLength)
    var suffix = 2
    while (!usedNames.add(candidate)) {
        val suffixText = "_$suffix"
        candidate = baseName
            .ifBlank { "mcp_tool" }
            .take(OpenAiToolNameMaxLength - suffixText.length) + suffixText
        suffix += 1
    }
    return candidate
}

private fun String.sanitizedToolPart(): String =
    trim()
        .lowercase()
        .map { char -> if (char.isLetterOrDigit() || char == '_') char else '_' }
        .joinToString(separator = "")
        .replace(Regex("_+"), "_")
        .trim('_')
        .ifBlank { "mcp" }

private fun Throwable.diagnosticMessage(): String =
    message ?: this::class.simpleName ?: "unknown"

private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

private const val LatestMcpProtocolVersion = "2025-06-18"
private const val McpProtocolVersionHeader = "mcp-protocol-version"
private const val McpSessionIdHeader = "mcp-session-id"
private const val OpenAiToolNameMaxLength = 64
