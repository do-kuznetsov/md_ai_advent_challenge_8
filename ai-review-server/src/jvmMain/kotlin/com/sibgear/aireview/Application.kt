package com.sibgear.aireview

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun Application.aiReviewServerModule(
    config: AiReviewConfig,
    reviewService: AiReviewService,
    signatureVerifier: WebhookSignatureVerifier = HmacSha256WebhookSignatureVerifier(config.githubWebhookSecret),
    reviewScope: CoroutineScope,
) {
    install(ContentNegotiation) {
        json(AiReviewJson)
    }

    routing {
        get("/health") {
            call.respondText("OK", ContentType.Text.Plain)
        }

        post("/github/webhook") {
            val event = call.request.header(GitHubEventHeader).orEmpty()
            val signature = call.request.header(GitHubSignatureHeader)
            val deliveryId = call.request.header(GitHubDeliveryHeader).orEmpty()
            val body = call.receiveText()
            if (!signatureVerifier.isValid(body.encodeToByteArray(), signature)) {
                call.respond(HttpStatusCode.Forbidden, "Invalid GitHub signature.")
                return@post
            }

            when (event) {
                "ping" -> call.respondText("pong", ContentType.Text.Plain)
                "pull_request" -> {
                    val payload = runCatching {
                        AiReviewJson.decodeFromString<PullRequestWebhookPayload>(body)
                    }.getOrElse { error ->
                        call.respond(HttpStatusCode.BadRequest, "Invalid pull_request payload: ${error.message}")
                        return@post
                    }
                    if (!payload.isSupportedAction || payload.repository.fullName != config.githubAllowedRepo) {
                        call.respondText("ignored", ContentType.Text.Plain)
                        return@post
                    }
                    reviewScope.launch {
                        reviewService.reviewPullRequest(payload, deliveryId)
                    }
                    call.respond(HttpStatusCode.Accepted, "accepted")
                }
                else -> call.respondText("ignored", ContentType.Text.Plain)
            }
        }
    }
}

private const val GitHubDeliveryHeader = "X-GitHub-Delivery"
private const val GitHubEventHeader = "X-GitHub-Event"
private const val GitHubSignatureHeader = "X-Hub-Signature-256"
