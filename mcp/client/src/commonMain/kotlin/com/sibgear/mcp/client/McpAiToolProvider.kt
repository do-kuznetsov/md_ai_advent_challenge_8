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
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

data class McpServerConnection(
    val id: Int,
    val name: String,
    val url: String,
    val isEnabled: Boolean,
)

class McpAiToolProvider(
    private val loadServers: () -> List<McpServerConnection>,
) : AiToolProvider {
    private val httpClient = HttpClient(CIO) {
        install(SSE)
    }

    override suspend fun openSession(): AiToolSession =
        connectSession()

    private suspend fun connectSession(): McpAiToolSession {
        val clients = mutableListOf<McpConnectedClient>()
        val tools = mutableListOf<AiToolDefinition>()
        val warnings = mutableListOf<String>()
        val usedNames = mutableSetOf<String>()
        val mappings = mutableMapOf<String, McpToolMapping>()

        loadServers()
            .filter { it.isEnabled }
            .forEach { server ->
                try {
                    val client = Client(
                        clientInfo = Implementation(
                            name = "deepseek-client",
                            version = "1.0.0",
                        ),
                    )
                    val transport = StreamableHttpClientTransport(
                        client = httpClient,
                        url = server.url,
                    )
                    client.connect(transport)
                    val connected = McpConnectedClient(server = server, client = client)
                    clients += connected
                    client.listTools().tools.forEach { tool ->
                        val exposedName = uniqueToolName(
                            baseName = "${server.name.sanitizedToolPart()}__${tool.name.sanitizedToolPart()}",
                            usedNames = usedNames,
                        )
                        mappings[exposedName] = McpToolMapping(
                            exposedName = exposedName,
                            toolName = tool.name,
                            connectedClient = connected,
                        )
                        tools += tool.toAiToolDefinition(exposedName)
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Throwable) {
                    warnings += "MCP сервер '${server.name}' недоступен: ${exception.message ?: exception::class.simpleName ?: "unknown"}"
                }
            }

        return McpAiToolSession(
            clients = clients,
            tools = tools,
            warnings = warnings,
            mappings = mappings,
        )
    }
}

internal class McpAiToolSession(
    val tools: List<AiToolDefinition>,
    val warnings: List<String>,
    private val clients: List<McpConnectedClient>,
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
            val result = mapping.connectedClient.client.callTool(
                name = mapping.toolName,
                arguments = invocation.arguments.toAnyMap(),
            )
            val isError = result.isError == true
            val content = result.textContent()
            AiToolResult(
                name = invocation.name,
                content = if (isError) {
                    "Ошибка MCP tool '${invocation.name}':\n$content"
                } else {
                    content
                },
                isError = isError,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Throwable) {
            AiToolResult(
                name = invocation.name,
                content = "Ошибка MCP tool '${invocation.name}': ${exception.message ?: exception::class.simpleName ?: "unknown"}",
                isError = true,
            )
        }
    }

    override suspend fun close() {
        clients.forEach { connected ->
            runCatching { connected.client.close() }
        }
    }
}

internal data class McpConnectedClient(
    val server: McpServerConnection,
    val client: Client,
)

internal data class McpToolMapping(
    val exposedName: String,
    val toolName: String,
    val connectedClient: McpConnectedClient,
)

private fun Tool.toAiToolDefinition(exposedName: String): AiToolDefinition =
    AiToolDefinition(
        name = exposedName,
        description = description,
        parameters = JsonObject(
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

private const val OpenAiToolNameMaxLength = 64
