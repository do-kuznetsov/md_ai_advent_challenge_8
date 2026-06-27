package com.sibgear.deepseek.chat.domain.model

import kotlinx.serialization.json.JsonObject

data class AiToolCatalog(
    val tools: List<AiToolDefinition> = emptyList(),
    val warnings: List<String> = emptyList(),
)

data class AiToolDefinition(
    val name: String,
    val description: String? = null,
    val parameters: JsonObject,
)

data class AiToolInvocation(
    val name: String,
    val arguments: JsonObject,
)

data class AiToolResult(
    val name: String,
    val content: String,
    val isError: Boolean = false,
)

interface AiToolProvider {
    suspend fun openSession(): AiToolSession

    suspend fun availableTools(): AiToolCatalog =
        withSession { session -> session.catalog }

    suspend fun callTool(invocation: AiToolInvocation): AiToolResult =
        withSession { session -> session.callTool(invocation) }

    suspend fun <T> withSession(block: suspend (AiToolSession) -> T): T {
        val session = openSession()
        return try {
            block(session)
        } finally {
            session.close()
        }
    }
}

interface AiToolSession {
    val catalog: AiToolCatalog
    suspend fun callTool(invocation: AiToolInvocation): AiToolResult
    suspend fun close()
}
