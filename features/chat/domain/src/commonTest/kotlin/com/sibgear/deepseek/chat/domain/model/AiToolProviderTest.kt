package com.sibgear.deepseek.chat.domain.model

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiToolProviderTest {
    @Test
    fun withSessionClosesOpenedSession() = runTest {
        val session = FakeToolSession()
        val provider = FakeToolProvider(session)

        val result = provider.withSession { opened ->
            assertEquals(session, opened)
            opened.callTool(AiToolInvocation(name = "test_tool", arguments = JsonObject(emptyMap())))
        }

        assertEquals(AiToolResult(name = "test_tool", content = "ok"), result)
        assertEquals(true, session.isClosed)
    }

    @Test
    fun compositeCatalogContainsToolsFromAllProviders() = runTest {
        val first = FakeToolSession(
            catalog = AiToolCatalog(
                tools = listOf(FakeToolDefinition("first_tool")),
                warnings = listOf("first warning"),
            ),
        )
        val second = FakeToolSession(
            catalog = AiToolCatalog(
                tools = listOf(FakeToolDefinition("second_tool")),
                warnings = listOf("second warning"),
            ),
        )
        val provider = CompositeAiToolProvider(
            listOf(
                FakeToolProvider(first),
                FakeToolProvider(second),
            ),
        )

        provider.withSession { session ->
            assertEquals(
                listOf("first_tool", "second_tool"),
                session.catalog.tools.map { it.name },
            )
            assertEquals(
                listOf("first warning", "second warning"),
                session.catalog.warnings,
            )
        }
    }

    @Test
    fun compositeRoutesToolCallToOwningSession() = runTest {
        val first = FakeToolSession(
            catalog = AiToolCatalog(tools = listOf(FakeToolDefinition("first_tool"))),
            responseContent = "first result",
        )
        val second = FakeToolSession(
            catalog = AiToolCatalog(tools = listOf(FakeToolDefinition("second_tool"))),
            responseContent = "second result",
        )
        val provider = CompositeAiToolProvider(
            listOf(
                FakeToolProvider(first),
                FakeToolProvider(second),
            ),
        )

        val result = provider.withSession { session ->
            session.callTool(AiToolInvocation(name = "second_tool", arguments = JsonObject(emptyMap())))
        }

        assertEquals(AiToolResult(name = "second_tool", content = "second result"), result)
        assertEquals(emptyList(), first.calls)
        assertEquals(listOf("second_tool"), second.calls)
        assertTrue(first.isClosed)
        assertTrue(second.isClosed)
    }

    @Test
    fun compositeReturnsErrorForUnknownTool() = runTest {
        val provider = CompositeAiToolProvider(
            listOf(
                FakeToolProvider(
                    FakeToolSession(
                        catalog = AiToolCatalog(tools = listOf(FakeToolDefinition("known_tool"))),
                    ),
                ),
            ),
        )

        val result = provider.withSession { session ->
            session.callTool(AiToolInvocation(name = "unknown_tool", arguments = JsonObject(emptyMap())))
        }

        assertEquals("unknown_tool", result.name)
        assertEquals(true, result.isError)
    }
}

private class FakeToolProvider(
    private val session: FakeToolSession,
) : AiToolProvider {
    override suspend fun openSession(): AiToolSession =
        session
}

private class FakeToolSession(
    override val catalog: AiToolCatalog = AiToolCatalog(),
    private val responseContent: String = "ok",
) : AiToolSession {
    var isClosed = false
        private set
    val calls = mutableListOf<String>()

    override suspend fun callTool(invocation: AiToolInvocation): AiToolResult {
        calls += invocation.name
        return AiToolResult(name = invocation.name, content = responseContent)
    }

    override suspend fun close() {
        isClosed = true
    }
}

private fun FakeToolDefinition(name: String): AiToolDefinition =
    AiToolDefinition(
        name = name,
        parameters = JsonObject(emptyMap()),
    )
