package com.sibgear.mcp.client

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class McpAiToolProviderTest {
    @Test
    fun disabledServersAreIgnored() = runTest {
        val provider = McpAiToolProvider(
            loadServers = {
                listOf(
                    McpServerConnection(
                        id = 1,
                        name = "local",
                        url = "http://127.0.0.1:1/mcp",
                        isEnabled = false,
                    ),
                )
            },
        )

        val catalog = provider.availableTools()

        assertEquals(emptyList(), catalog.tools)
        assertEquals(emptyList(), catalog.warnings)
    }
}
