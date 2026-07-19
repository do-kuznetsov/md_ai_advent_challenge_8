package com.sibgear.aireview

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface GitHubClient {
    suspend fun listPullRequestFiles(ownerRepo: OwnerRepo, pullNumber: Int): List<GitHubChangedFile>
    suspend fun getPullRequestDiff(ownerRepo: OwnerRepo, pullNumber: Int): String
    suspend fun listPullRequestReviews(ownerRepo: OwnerRepo, pullNumber: Int): List<GitHubReview>
    suspend fun createPullRequestReview(request: GitHubCreateReviewRequest)
}

class GitHubReviewPublicationException(message: String) : RuntimeException(message)

class KtorGitHubClient(
    private val config: AiReviewConfig,
    private val client: HttpClient = defaultGitHubHttpClient(),
) : GitHubClient {
    private val apiBaseUrl = config.githubApiBaseUrl.trimEnd('/')

    override suspend fun listPullRequestFiles(ownerRepo: OwnerRepo, pullNumber: Int): List<GitHubChangedFile> =
        client.get("$apiBaseUrl/repos/${ownerRepo.owner}/${ownerRepo.repo}/pulls/$pullNumber/files?per_page=100") {
            bearerAuth(config.githubToken)
            githubHeaders()
        }.ensureSuccess("list pull request files")
            .body<List<GitHubFileDto>>()
            .map { it.toDomain() }

    override suspend fun getPullRequestDiff(ownerRepo: OwnerRepo, pullNumber: Int): String =
        client.get("$apiBaseUrl/repos/${ownerRepo.owner}/${ownerRepo.repo}/pulls/$pullNumber") {
            bearerAuth(config.githubToken)
            accept(ContentType.parse("application/vnd.github.v3.diff"))
            header("X-GitHub-Api-Version", GitHubApiVersion)
        }.ensureSuccess("get pull request diff")
            .bodyAsText()

    override suspend fun listPullRequestReviews(ownerRepo: OwnerRepo, pullNumber: Int): List<GitHubReview> =
        client.get("$apiBaseUrl/repos/${ownerRepo.owner}/${ownerRepo.repo}/pulls/$pullNumber/reviews?per_page=100") {
            bearerAuth(config.githubToken)
            githubHeaders()
        }.ensureSuccess("list pull request reviews")
            .body<List<GitHubReviewDto>>()
            .map { GitHubReview(id = it.id, body = it.body.orEmpty()) }

    override suspend fun createPullRequestReview(request: GitHubCreateReviewRequest) {
        val response = client.post(
            "$apiBaseUrl/repos/${request.ownerRepo.owner}/${request.ownerRepo.repo}/pulls/${request.pullNumber}/reviews",
        ) {
            bearerAuth(config.githubToken)
            githubHeaders()
            contentType(ContentType.Application.Json)
            setBody(request.toDto())
        }
        if (!response.status.isSuccess()) {
            throw GitHubReviewPublicationException(
                "GitHub create review HTTP ${response.status.value}: ${response.bodyAsText().take(600)}",
            )
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.githubHeaders() {
        accept(ContentType.Application.Json)
        header("X-GitHub-Api-Version", GitHubApiVersion)
    }
}

private fun io.ktor.client.statement.HttpResponse.ensureSuccess(operation: String): io.ktor.client.statement.HttpResponse {
    if (!status.isSuccess()) {
        error("GitHub $operation HTTP ${status.value}")
    }
    return this
}

private fun defaultGitHubHttpClient(): HttpClient =
    HttpClient(io.ktor.client.engine.cio.CIO) {
        install(ContentNegotiation) {
            json(AiReviewJson)
        }
    }

@Serializable
private data class GitHubFileDto(
    val filename: String,
    val status: String,
    val additions: Int,
    val deletions: Int,
    val changes: Int,
    val patch: String? = null,
) {
    fun toDomain(): GitHubChangedFile =
        GitHubChangedFile(
            filename = filename,
            status = status,
            additions = additions,
            deletions = deletions,
            changes = changes,
            patch = patch,
        )
}

@Serializable
private data class GitHubReviewDto(
    val id: Long,
    val body: String? = null,
)

@Serializable
private data class CreateReviewDto(
    @SerialName("commit_id")
    val commitId: String,
    val body: String,
    val event: String = "COMMENT",
    val comments: List<CreateReviewCommentDto>? = null,
)

@Serializable
private data class CreateReviewCommentDto(
    val path: String,
    val line: Int,
    val side: String,
    val body: String,
)

private fun GitHubCreateReviewRequest.toDto(): CreateReviewDto =
    CreateReviewDto(
        commitId = commitId,
        body = body,
        comments = comments.map {
            CreateReviewCommentDto(
                path = it.path,
                line = it.line,
                side = it.side,
                body = it.body,
            )
        }.takeIf { it.isNotEmpty() },
    )

private const val GitHubApiVersion = "2026-03-10"
