package com.sibgear.mcp.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

fun main(args: Array<String>) = runBlocking {
    val url = args.firstOrNull()
    if (url.isNullOrBlank()) {
        println("Ошибка: не передан URL MCP сервера.")
        printUsage()
        return@runBlocking
    }

    HttpClient(CIO) {
        install(SSE)
    }.use { httpClient ->
        val client = Client(
            clientInfo = Implementation(
                name = "visitor-log-cli-client",
                version = "1.0.0",
            ),
        )
        val transport = StreamableHttpClientTransport(
            client = httpClient,
            url = url,
        )

        try {
            client.connect(transport)
            val tools = client.listTools().tools
            printTools(tools)

            val selectedTool = readSelectedTool(tools) ?: return@use
            val arguments = readToolArguments(selectedTool) ?: return@use
            val result = client.callTool(
                name = selectedTool.name,
                arguments = arguments,
            )

            printToolResult(result)
        } catch (error: Throwable) {
            println("Ошибка: ${error.message ?: error::class.simpleName}")
        }
    }
}

private fun printTools(tools: List<Tool>) {
    println("Доступные tools:")
    tools.forEach { tool ->
        val description = tool.description?.takeIf { it.isNotBlank() } ?: "без описания"
        println("- ${tool.name}: $description")
    }
}

private fun readSelectedTool(tools: List<Tool>): Tool? {
    println("Введите название tool:")
    val toolName = readlnOrNull()?.trim().orEmpty()
    val selectedTool = tools.firstOrNull { it.name == toolName }
    if (selectedTool == null) {
        println("Tool '$toolName' не найден.")
    }
    return selectedTool
}

private fun readToolArguments(tool: Tool): Map<String, Any>? {
    val required = tool.inputSchema.required.orEmpty()
    val requiredNames = required.toSet()
    val properties = tool.inputSchema.properties ?: JsonObject(emptyMap())
    val orderedPropertyNames = required +
        properties.keys.filterNot { it in requiredNames }
    val arguments = mutableMapOf<String, Any>()

    orderedPropertyNames.forEach { name ->
        val schema = properties[name] as? JsonObject ?: JsonObject(emptyMap())
        val type = schema.stringValue("type") ?: "string"
        val description = schema.stringValue("description") ?: "без описания"
        val requiredLabel = if (name in requiredNames) ", required" else ""

        println("Введите $name (type: $type$requiredLabel): $description")
        val rawValue = readlnOrNull()
        if (rawValue == null) {
            println("Ошибка: ввод завершен до получения аргумента '$name'.")
            return null
        }
        if (name in requiredNames && rawValue.isBlank()) {
            println("Ошибка: обязательный аргумент '$name' не может быть пустым.")
            return null
        }

        val parsedValue = parseArgumentValue(name, schema, rawValue) ?: return null
        arguments[name] = parsedValue
    }

    return arguments
}

private fun parseArgumentValue(
    name: String,
    schema: JsonObject,
    rawValue: String,
): Any? =
    when (schema.stringValue("type")) {
        "integer" -> rawValue.toIntOrNull() ?: run {
            println("Ошибка: аргумент '$name' должен быть integer.")
            null
        }

        "number" -> rawValue.toDoubleOrNull() ?: run {
            println("Ошибка: аргумент '$name' должен быть number.")
            null
        }

        "boolean" -> when (rawValue.lowercase()) {
            "true" -> true
            "false" -> false
            else -> {
                println("Ошибка: аргумент '$name' должен быть boolean: true или false.")
                null
            }
        }

        else -> rawValue
    }

private fun printToolResult(result: CallToolResult) {
    if (result.isError == true) {
        println("Ошибка tool:")
    }

    result.content.forEach { content ->
        when (content) {
            is TextContent -> println(content.text)
            else -> println(content.toString())
        }
    }
}

private fun printUsage() {
    println("Использование:")
    println("./gradlew -q :mcp:client:jvmRun --args='http://127.0.0.1:3000/mcp'")
}

private fun JsonObject.stringValue(key: String): String? =
    get(key)?.jsonPrimitive?.contentOrNull
