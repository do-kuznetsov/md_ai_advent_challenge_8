package com.sibgear.deepseek.chat.domain.model

class CompositeAiToolProvider(
    private val providers: List<AiToolProvider>,
) : AiToolProvider {
    override suspend fun openSession(): AiToolSession {
        val sessions = mutableListOf<AiToolSession>()
        return try {
            providers.forEach { provider ->
                sessions += provider.openSession()
            }
            CompositeAiToolSession(sessions)
        } catch (throwable: Throwable) {
            sessions.forEach { session ->
                runCatching { session.close() }
            }
            throw throwable
        }
    }
}

private class CompositeAiToolSession(
    private val sessions: List<AiToolSession>,
) : AiToolSession {
    private val toolSessions: Map<String, AiToolSession>
    override val catalog: AiToolCatalog

    init {
        val tools = mutableListOf<AiToolDefinition>()
        val warnings = mutableListOf<String>()
        val routedSessions = linkedMapOf<String, AiToolSession>()

        sessions.forEach { session ->
            warnings += session.catalog.warnings
            session.catalog.tools.forEach { tool ->
                if (routedSessions.containsKey(tool.name)) {
                    warnings += "Tool '${tool.name}' объявлен несколько раз; используется первое объявление."
                } else {
                    routedSessions[tool.name] = session
                    tools += tool
                }
            }
        }

        toolSessions = routedSessions
        catalog = AiToolCatalog(
            tools = tools,
            warnings = warnings,
        )
    }

    override suspend fun callTool(invocation: AiToolInvocation): AiToolResult {
        val session = toolSessions[invocation.name]
            ?: return AiToolResult(
                name = invocation.name,
                content = "Tool '${invocation.name}' не найден.",
                isError = true,
            )

        return session.callTool(invocation)
    }

    override suspend fun close() {
        sessions.forEach { session ->
            runCatching { session.close() }
        }
    }
}
