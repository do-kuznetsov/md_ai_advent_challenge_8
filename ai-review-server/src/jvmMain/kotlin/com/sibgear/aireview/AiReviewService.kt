package com.sibgear.aireview

import kotlinx.coroutines.CancellationException

class AiReviewService(
    private val config: AiReviewConfig,
    private val githubClient: GitHubClient,
    private val assistant: ReviewAssistant,
    private val ragProvider: ReviewRagProvider,
) {
    suspend fun reviewPullRequest(
        payload: PullRequestWebhookPayload,
        deliveryId: String,
    ): ReviewRunResult {
        if (!payload.isSupportedAction || payload.repository.fullName != config.githubAllowedRepo) {
            return ReviewRunResult.Ignored
        }

        val ownerRepo = OwnerRepo.parse(payload.repository.fullName)
        val pullNumber = payload.pullRequest.number
        val headSha = payload.pullRequest.head.sha
        val marker = reviewMarker(ownerRepo.fullName, pullNumber, headSha)
        if (githubClient.listPullRequestReviews(ownerRepo, pullNumber).any { it.isCompletedAiReview(marker) }) {
            return ReviewRunResult.Duplicate
        }

        return try {
            val files = githubClient.listPullRequestFiles(ownerRepo, pullNumber)
                .take(config.maxFiles)
            val diff = githubClient.getPullRequestDiff(ownerRepo, pullNumber)
                .take(config.maxPromptChars)
            val ragQuery = buildRagQuery(payload, files, diff)
            val ragChunks = runCatching { ragProvider.findContext(ragQuery) }.getOrDefault(emptyList())
            val reviewContext = PullRequestReviewContext(
                ownerRepo = ownerRepo,
                number = pullNumber,
                title = payload.pullRequest.title,
                body = payload.pullRequest.body.orEmpty(),
                headSha = headSha,
                baseSha = payload.pullRequest.base.sha,
                changedFiles = files,
                diff = diff,
                ragChunks = ragChunks,
            )
            val changedLines = UnifiedDiffChangedLineParser.parse(diff)
            val assistantResult = assistant.review(reviewContext)
            val validatedReview = ReviewResultValidator(config.maxInlineComments)
                .validate(assistantResult, changedLines)
            val body = ReviewBodyFormatter.format(
                marker = marker,
                deliveryId = deliveryId,
                review = validatedReview,
            )
            publishReview(
                ownerRepo = ownerRepo,
                pullNumber = pullNumber,
                headSha = headSha,
                body = body,
                comments = validatedReview.inlineComments,
            )
            ReviewRunResult.Published
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Throwable) {
            val body = buildFailureBody(
                marker = marker,
                deliveryId = deliveryId,
                error = exception,
            )
            publishReview(
                ownerRepo = ownerRepo,
                pullNumber = pullNumber,
                headSha = headSha,
                body = body,
                comments = emptyList(),
            )
            ReviewRunResult.Published
        }
    }

    private suspend fun publishReview(
        ownerRepo: OwnerRepo,
        pullNumber: Int,
        headSha: String,
        body: String,
        comments: List<GitHubReviewComment>,
    ) {
        val request = GitHubCreateReviewRequest(
            ownerRepo = ownerRepo,
            pullNumber = pullNumber,
            commitId = headSha,
            body = body,
            comments = comments,
        )
        if (comments.isEmpty()) {
            githubClient.createPullRequestReview(request)
            return
        }

        try {
            githubClient.createPullRequestReview(request)
        } catch (exception: GitHubReviewPublicationException) {
            githubClient.createPullRequestReview(
                request.copy(
                    body = body + "\n\n" + comments.toFallbackSection(),
                    comments = emptyList(),
                ),
            )
        }
    }

    private fun buildRagQuery(
        payload: PullRequestWebhookPayload,
        files: List<GitHubChangedFile>,
        diff: String,
    ): String =
        buildString {
            appendLine(payload.pullRequest.title)
            payload.pullRequest.body?.takeIf { it.isNotBlank() }?.let { appendLine(it) }
            files.forEach { appendLine("${it.filename} ${it.status}") }
            appendLine(diff.take(10_000))
        }

    private fun buildFailureBody(
        marker: String,
        deliveryId: String,
        error: Throwable,
    ): String =
        buildString {
            appendLine(marker)
            appendLine("## AI Review")
            appendLine()
            appendLine("**Вердикт:** нужно доработать")
            appendLine()
            appendLine(FailedReviewText)
            appendLine()
            appendLine("Ошибка: `${error.message ?: error::class.simpleName ?: "unknown"}`")
            if (deliveryId.isNotBlank()) {
                appendLine()
                appendLine("delivery: `$deliveryId`")
            }
        }
}

fun reviewMarker(
    repoFullName: String,
    pullNumber: Int,
    headSha: String,
): String =
    "<!-- ai-review:$repoFullName#$pullNumber:$headSha -->"

private const val FailedReviewText = "Автоматическое ревью не удалось завершить."

private fun GitHubReview.isCompletedAiReview(marker: String): Boolean =
    marker in body && FailedReviewText !in body

private fun List<GitHubReviewComment>.toFallbackSection(): String {
    val comments = this
    return buildString {
        appendLine("## Точечные замечания")
        comments.forEach { comment ->
            appendLine("- `${comment.path}:${comment.line}` ${comment.body}")
        }
    }.trimEnd()
}
