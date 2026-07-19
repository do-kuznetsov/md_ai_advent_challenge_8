package com.sibgear.aireview

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

fun main() {
    val config = AiReviewConfig()
    config.requireReady()

    val service = AiReviewService(
        config = config,
        githubClient = KtorGitHubClient(config),
        assistant = DeepSeekReviewAssistant(config),
        ragProvider = OnnxReviewRagProvider(config),
    )
    val server = embeddedServer(CIO, host = config.host, port = config.port) {
        aiReviewServerModule(
            config = config,
            reviewService = service,
            reviewScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )
    }
    server.start(wait = false)
    println("AI Review server: http://${config.host}:${config.port}")
    Thread.currentThread().join()
}
