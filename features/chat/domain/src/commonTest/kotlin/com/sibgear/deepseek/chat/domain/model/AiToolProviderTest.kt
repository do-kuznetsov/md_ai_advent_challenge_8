package com.sibgear.deepseek.chat.domain.model

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

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
}

private class FakeToolProvider(
    private val session: FakeToolSession,
) : AiToolProvider {
    override suspend fun openSession(): AiToolSession =
        session
}

private class FakeToolSession : AiToolSession {
    var isClosed = false
        private set

    override val catalog: AiToolCatalog =
        AiToolCatalog()

    override suspend fun callTool(invocation: AiToolInvocation): AiToolResult =
        AiToolResult(name = invocation.name, content = "ok")

    override suspend fun close() {
        isClosed = true
    }
}
