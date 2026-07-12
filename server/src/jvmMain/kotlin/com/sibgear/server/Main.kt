package com.sibgear.server

import com.sibgear.server.protocol.ChatRequest
import com.sibgear.server.protocol.ChatStreamEvent
import com.sibgear.server.protocol.ServerProtocolJson
import com.sibgear.server.protocol.ServerPublicConfig
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.SendChannel

fun main() {
    val config = ServerConfig()
    embeddedServer(CIO, host = config.host, port = config.port) {
        serverModule(config)
    }.start(wait = true)
}

fun Application.serverModule(
    config: ServerConfig = ServerConfig(),
    chatService: ChatStreamer = ChatService(config),
) {
    install(ContentNegotiation) {
        json(ServerProtocolJson)
    }
    install(WebSockets)

    routing {
        get("/health") {
            call.respondText("OK", ContentType.Text.Plain)
        }
        get("/api/config") {
            call.respond(
                ServerPublicConfig(
                    modelId = config.llamaModelId,
                    contextSize = config.llamaContextSize,
                ),
            )
        }
        webSocket("/api/chat") {
            val frame = incoming.receive() as? Frame.Text
            if (frame == null) {
                outgoing.sendEvent(ChatStreamEvent.Error("Expected text websocket frame."))
                return@webSocket
            }

            val request = runCatching {
                ServerProtocolJson.decodeFromString<ChatRequest>(frame.readText())
            }.getOrElse { error ->
                outgoing.sendEvent(ChatStreamEvent.Error("Invalid chat request: ${error.message ?: "unknown"}"))
                return@webSocket
            }

            chatService.stream(request) { event ->
                outgoing.sendEvent(event)
            }
        }
        staticResources("/", "static")
    }
}

private suspend fun SendChannel<Frame>.sendEvent(event: ChatStreamEvent) {
    send(Frame.Text(ServerProtocolJson.encodeToString(ChatStreamEvent.serializer(), event)))
}
