package com.sibgear.aireview

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.http.content.OutgoingContent
import io.ktor.server.testing.testApplication
import io.ktor.serialization.kotlinx.json.json
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AiReviewServerTest {
    @Test
    fun hmacVerifierAcceptsValidSignatureAndRejectsInvalidOne() {
        val verifier = HmacSha256WebhookSignatureVerifier("secret")
        val payload = """{"zen":"Keep it logically awesome."}""".encodeToByteArray()

        assertTrue(verifier.isValid(payload, payload.signature("secret")))
        assertFalse(verifier.isValid(payload, payload.signature("wrong")))
    }

    @Test
    fun configFailsFastWhenRequiredSecretsAreMissing() {
        val config = AiReviewConfig(
            githubToken = "",
            githubWebhookSecret = "secret",
            githubAllowedRepo = "owner/repo",
            deepSeekApiKey = "deepseek-token",
        )

        val error = kotlin.test.assertFailsWith<IllegalArgumentException> {
            config.requireReady()
        }

        assertTrue(error.message.orEmpty().contains("github_token"))
    }

    @Test
    fun pingWebhookReturnsPong() = testApplication {
        application {
            aiReviewServerModule(
                config = testConfig(),
                reviewService = fakeService(),
                reviewScope = CoroutineScope(Dispatchers.Unconfined),
            )
        }
        val payload = """{"zen":"ok"}"""

        val response = client.post("/github/webhook") {
            header("X-GitHub-Event", "ping")
            header("X-Hub-Signature-256", payload.encodeToByteArray().signature("webhook-secret"))
            contentType(ContentType.Application.Json)
            setBody(payload)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("pong", response.bodyAsText())
    }

    @Test
    fun pullRequestWebhookStartsReviewJob() = testApplication {
        val githubClient = FakeGitHubClient()
        val service = AiReviewService(
            config = testConfig(),
            githubClient = githubClient,
            assistant = FakeReviewAssistant(
                AiReviewResult(
                    verdict = "needs_work",
                    summary = "Найден потенциальный баг.",
                    bugs = listOf("Null handling"),
                    findings = listOf(
                        AiReviewFinding(
                            category = "bug",
                            severity = "high",
                            path = "src/App.kt",
                            line = 10,
                            body = "Проверь null перед использованием.",
                        ),
                    ),
                ),
            ),
            ragProvider = FakeRagProvider(),
        )
        application {
            aiReviewServerModule(
                config = testConfig(),
                reviewService = service,
                reviewScope = CoroutineScope(Dispatchers.Unconfined),
            )
        }
        val payload = pullRequestPayload()

        val response = client.post("/github/webhook") {
            header("X-GitHub-Event", "pull_request")
            header("X-GitHub-Delivery", UUID.randomUUID().toString())
            header("X-Hub-Signature-256", payload.encodeToByteArray().signature("webhook-secret"))
            contentType(ContentType.Application.Json)
            setBody(payload)
        }

        assertEquals(HttpStatusCode.Accepted, response.status)
        assertEquals(1, githubClient.createdReviews.size)
        val review = githubClient.createdReviews.single()
        assertEquals("abc123", review.commitId)
        assertTrue(review.body.contains("нужно доработать"))
        assertEquals(listOf(GitHubReviewComment("src/App.kt", 10, "RIGHT", "[bug/high] Проверь null перед использованием.")), review.comments)
    }

    @Test
    fun wrongRepoIsIgnored() = testApplication {
        val githubClient = FakeGitHubClient()
        val service = fakeService(githubClient)
        application {
            aiReviewServerModule(
                config = testConfig(githubAllowedRepo = "another/repo"),
                reviewService = service,
                reviewScope = CoroutineScope(Dispatchers.Unconfined),
            )
        }
        val payload = pullRequestPayload()

        val response = client.post("/github/webhook") {
            header("X-GitHub-Event", "pull_request")
            header("X-Hub-Signature-256", payload.encodeToByteArray().signature("webhook-secret"))
            contentType(ContentType.Application.Json)
            setBody(payload)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ignored", response.bodyAsText())
        assertEquals(0, githubClient.createdReviews.size)
    }

    @Test
    fun serviceSkipsAlreadyReviewedHeadSha() = runTest {
        val githubClient = FakeGitHubClient(
            reviews = listOf(
                GitHubReview(
                    id = 1,
                    body = """
                        ${reviewMarker("owner/repo", 7, "abc123")}
                        ## AI Review
                        Ревью завершено.
                    """.trimIndent(),
                ),
            ),
        )
        val service = fakeService(githubClient)

        val result = service.reviewPullRequest(
            payload = AiReviewJson.decodeFromString(pullRequestPayload()),
            deliveryId = "delivery",
        )

        assertIs<ReviewRunResult.Duplicate>(result)
        assertEquals(0, githubClient.createdReviews.size)
    }

    @Test
    fun serviceDoesNotSkipFailedReviewForSameHeadSha() = runTest {
        val githubClient = FakeGitHubClient(
            reviews = listOf(
                GitHubReview(
                    id = 1,
                    body = """
                        ${reviewMarker("owner/repo", 7, "abc123")}
                        ## AI Review
                        Автоматическое ревью не удалось завершить.
                    """.trimIndent(),
                ),
            ),
        )
        val service = fakeService(githubClient)

        val result = service.reviewPullRequest(
            payload = AiReviewJson.decodeFromString(pullRequestPayload()),
            deliveryId = "delivery",
        )

        assertIs<ReviewRunResult.Published>(result)
        assertEquals(1, githubClient.createdReviews.size)
    }

    @Test
    fun diffParserTracksOnlyAddedLines() {
        val diff = """
            diff --git a/src/App.kt b/src/App.kt
            index 111..222 100644
            --- a/src/App.kt
            +++ b/src/App.kt
            @@ -8,4 +8,5 @@ fun main() {
             val old = 1
            -println(old)
            +val next = old + 1
            +println(next)
             }
        """.trimIndent()

        val changedLines = UnifiedDiffChangedLineParser.parse(diff)

        assertEquals(setOf(9, 10), changedLines["src/App.kt"])
    }

    @Test
    fun validatorKeepsInlineOnlyForChangedLines() {
        val review = AiReviewResult(
            verdict = "needs_work",
            summary = "summary",
            findings = listOf(
                AiReviewFinding("bug", "high", "src/App.kt", 10, "inline"),
                AiReviewFinding("bug", "high", "src/App.kt", 11, "fallback"),
                AiReviewFinding("architecture", "medium", null, null, "file level"),
            ),
        )

        val validated = ReviewResultValidator(maxInlineComments = 20)
            .validate(review, mapOf("src/App.kt" to setOf(10)))

        assertEquals(1, validated.inlineComments.size)
        assertEquals(2, validated.fallbackNotes.size)
    }

    @Test
    fun githubCreateReviewRequestIncludesCommentEvent() = runTest {
        var requestBody = ""
        val client = io.ktor.client.HttpClient(
            MockEngine { request ->
                requestBody = request.body.requestText()
                respond(
                    content = "{}",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
        ) {
            install(ContentNegotiation) {
                json(AiReviewJson)
            }
        }
        val github = KtorGitHubClient(
            config = testConfig().copy(githubApiBaseUrl = "https://api.github.test"),
            client = client,
        )

        github.createPullRequestReview(
            GitHubCreateReviewRequest(
                ownerRepo = OwnerRepo("owner", "repo"),
                pullNumber = 7,
                commitId = "abc123",
                body = "review body",
                comments = emptyList(),
            ),
        )

        assertTrue(requestBody.contains(""""event":"COMMENT""""))
    }

    @Test
    fun deepSeekAssistantRequestsJsonOutputAndParsesFencedJson() = runTest {
        var requestBody = ""
        val client = io.ktor.client.HttpClient(
            MockEngine { request ->
                requestBody = request.body.requestText()
                respond(
                    content = """
                    {
                      "choices": [
                        {
                          "message": {
                            "content": "```json\n{\"verdict\":\"ok_to_merge\",\"summary\":\"LGTM\",\"bugs\":[],\"architecture_problems\":[],\"recommendations\":[],\"findings\":[]}\n```"
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
        ) {
            install(ContentNegotiation) {
                json(AiReviewJson)
            }
        }
        val assistant = DeepSeekReviewAssistant(testConfig(), client)

        val result = assistant.review(testReviewContext())

        assertEquals("ok_to_merge", result.verdict)
        assertTrue(requestBody.contains(""""response_format":{"type":"json_object"}"""))
        assertTrue(requestBody.contains(""""thinking":{"type":"enabled"}"""))
    }

    @Test
    fun jsonExtractorKeepsNestedBracesInsideStrings() {
        val response = """
            Explanation before JSON.
            ```json
            {"summary":"Uses braces like {value} in text","findings":[{"body":"Fix {placeholder}"}]}
            ```
            Explanation after JSON.
        """.trimIndent()

        val json = response.extractAiReviewJsonObject()

        assertTrue(json.startsWith("{\"summary\""))
        assertTrue(json.contains("Fix {placeholder}"))
    }

    @Test
    fun inlinePublicationFailureFallsBackToBodyOnlyReview() = runTest {
        val githubClient = FakeGitHubClient(failFirstCreateReview = true)
        val service = fakeService(githubClient)

        service.reviewPullRequest(
            payload = AiReviewJson.decodeFromString(pullRequestPayload()),
            deliveryId = "delivery",
        )

        assertEquals(2, githubClient.createdReviews.size)
        assertTrue(githubClient.createdReviews.first().comments.isNotEmpty())
        assertTrue(githubClient.createdReviews.last().comments.isEmpty())
        assertTrue(githubClient.createdReviews.last().body.contains("Точечные замечания"))
    }

    private fun fakeService(githubClient: FakeGitHubClient = FakeGitHubClient()): AiReviewService =
        AiReviewService(
            config = testConfig(),
            githubClient = githubClient,
            assistant = FakeReviewAssistant(
                AiReviewResult(
                    verdict = "needs_work",
                    summary = "summary",
                    findings = listOf(
                        AiReviewFinding("bug", "high", "src/App.kt", 10, "inline"),
                    ),
                ),
            ),
            ragProvider = FakeRagProvider(),
        )

    private fun testConfig(
        githubAllowedRepo: String = "owner/repo",
    ): AiReviewConfig =
        AiReviewConfig(
            githubToken = "github-token",
            githubWebhookSecret = "webhook-secret",
            githubAllowedRepo = githubAllowedRepo,
            deepSeekApiKey = "deepseek-token",
        )

    private fun pullRequestPayload(): String =
        """
        {
          "action": "opened",
          "repository": {"full_name": "owner/repo"},
          "pull_request": {
            "number": 7,
            "title": "Fix app",
            "body": "PR body",
            "head": {"sha": "abc123", "ref": "feature"},
            "base": {"sha": "def456", "ref": "main"}
          }
        }
        """.trimIndent()

    private fun testReviewContext(): PullRequestReviewContext =
        PullRequestReviewContext(
            ownerRepo = OwnerRepo("owner", "repo"),
            number = 7,
            title = "Fix app",
            body = "PR body",
            headSha = "abc123",
            baseSha = "def456",
            changedFiles = listOf(
                GitHubChangedFile(
                    filename = "src/App.kt",
                    status = "modified",
                    additions = 1,
                    deletions = 0,
                    changes = 1,
                    patch = "@@ -9,0 +10,1 @@\n+println(next)",
                ),
            ),
            diff = """
                diff --git a/src/App.kt b/src/App.kt
                index 111..222 100644
                --- a/src/App.kt
                +++ b/src/App.kt
                @@ -9,0 +10,1 @@
                +println(next)
            """.trimIndent(),
            ragChunks = emptyList(),
        )
}

private class FakeGitHubClient(
    private val reviews: List<GitHubReview> = emptyList(),
    private val failFirstCreateReview: Boolean = false,
) : GitHubClient {
    val createdReviews: MutableList<GitHubCreateReviewRequest> = mutableListOf()

    override suspend fun listPullRequestFiles(ownerRepo: OwnerRepo, pullNumber: Int): List<GitHubChangedFile> =
        listOf(
            GitHubChangedFile(
                filename = "src/App.kt",
                status = "modified",
                additions = 1,
                deletions = 0,
                changes = 1,
                patch = "@@ -9,1 +10,1 @@\n+println(next)",
            ),
        )

    override suspend fun getPullRequestDiff(ownerRepo: OwnerRepo, pullNumber: Int): String =
        """
        diff --git a/src/App.kt b/src/App.kt
        index 111..222 100644
        --- a/src/App.kt
        +++ b/src/App.kt
        @@ -9,0 +10,1 @@
        +println(next)
        """.trimIndent()

    override suspend fun listPullRequestReviews(ownerRepo: OwnerRepo, pullNumber: Int): List<GitHubReview> =
        reviews

    override suspend fun createPullRequestReview(request: GitHubCreateReviewRequest) {
        createdReviews += request
        if (failFirstCreateReview && createdReviews.size == 1) {
            throw GitHubReviewPublicationException("422 validation failed")
        }
    }
}

private class FakeReviewAssistant(
    private val result: AiReviewResult,
) : ReviewAssistant {
    override suspend fun review(context: PullRequestReviewContext): AiReviewResult =
        result
}

private class FakeRagProvider : ReviewRagProvider {
    override suspend fun findContext(query: String): List<ReviewRagChunk> =
        listOf(
            ReviewRagChunk(
                source = "README.md",
                section = "Architecture",
                chunkId = "readme-1",
                text = "Project architecture documentation.",
            ),
        )
}

private fun ByteArray.signature(secret: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.encodeToByteArray(), "HmacSHA256"))
    return "sha256=" + mac.doFinal(this).joinToString(separator = "") { "%02x".format(it) }
}

private fun Any.requestText(): String =
    ((this as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString()).orEmpty()
