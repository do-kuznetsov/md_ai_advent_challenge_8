package com.sibgear.server

import com.sibgear.server.protocol.ChatRequest
import com.sibgear.server.protocol.ChatStreamEvent
import com.sibgear.server.protocol.ServerProtocolJson
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ServerApplicationTest {
    @Test
    fun healthReturnsOk() = testApplication {
        application {
            serverModule(
                config = testConfig(),
                chatService = FakeChatStreamer(),
            )
        }

        assertEquals("OK", client.get("/health").bodyAsText())
    }

    @Test
    fun configReturnsPublicConfig() = testApplication {
        application {
            serverModule(
                config = testConfig(),
                chatService = FakeChatStreamer(),
            )
        }

        val body = client.get("/api/config").bodyAsText()

        assertEquals(true, body.contains("qwen-test"))
        assertEquals(true, body.contains("4096"))
    }

    @Test
    fun chatWebsocketStreamsEvents() = testApplication {
        application {
            serverModule(
                config = testConfig(),
                chatService = FakeChatStreamer(),
            )
        }
        val wsClient = createClient {
            install(WebSockets)
        }

        wsClient.webSocket("/api/chat") {
            send(Frame.Text(ServerProtocolJson.encodeToString(ChatRequest.serializer(), ChatRequest("hello"))))

            val context = receiveEvent()
            val content = receiveEvent()
            val done = receiveEvent()

            assertIs<ChatStreamEvent.Context>(context)
            assertEquals("world", assertIs<ChatStreamEvent.ContentDelta>(content).text)
            assertEquals("world", assertIs<ChatStreamEvent.Done>(done).content)
        }
    }

    private suspend fun io.ktor.client.plugins.websocket.DefaultClientWebSocketSession.receiveEvent(): ChatStreamEvent =
        ServerProtocolJson.decodeFromString(
            ChatStreamEvent.serializer(),
            (incoming.receive() as Frame.Text).readText(),
        )

    private fun testConfig(): ServerConfig =
        ServerConfig(
            llamaModelId = "qwen-test",
            llamaContextSize = 4096,
        )
}

private class FakeChatStreamer : ChatStreamer {
    override suspend fun stream(
        request: ChatRequest,
        emit: suspend (ChatStreamEvent) -> Unit,
    ) {
        emit(ChatStreamEvent.Context(usedTokens = 10, maxTokens = 4096))
        emit(ChatStreamEvent.ContentDelta("world"))
        emit(ChatStreamEvent.Done(content = "world", usedTokens = 11, maxTokens = 4096))
    }
}
