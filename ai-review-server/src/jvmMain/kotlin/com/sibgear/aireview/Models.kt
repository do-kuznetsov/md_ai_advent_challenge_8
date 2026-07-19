package com.sibgear.aireview

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PullRequestWebhookPayload(
    val action: String,
    val repository: GitHubRepositoryPayload,
    @SerialName("pull_request")
    val pullRequest: GitHubPullRequestPayload,
) {
    val isSupportedAction: Boolean
        get() = action == "opened" || action == "synchronize"
}

@Serializable
data class GitHubRepositoryPayload(
    @SerialName("full_name")
    val fullName: String,
)

@Serializable
data class GitHubPullRequestPayload(
    val number: Int,
    val title: String,
    val body: String? = null,
    val head: GitHubRefPayload,
    val base: GitHubRefPayload,
)

@Serializable
data class GitHubRefPayload(
    val sha: String,
    val ref: String? = null,
)

data class OwnerRepo(
    val owner: String,
    val repo: String,
) {
    val fullName: String = "$owner/$repo"

    companion object {
        fun parse(fullName: String): OwnerRepo {
            val parts = fullName.split('/', limit = 2)
            require(parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                "Invalid repository full name: $fullName"
            }
            return OwnerRepo(owner = parts[0], repo = parts[1])
        }
    }
}

data class PullRequestReviewContext(
    val ownerRepo: OwnerRepo,
    val number: Int,
    val title: String,
    val body: String,
    val headSha: String,
    val baseSha: String,
    val changedFiles: List<GitHubChangedFile>,
    val diff: String,
    val ragChunks: List<ReviewRagChunk>,
)

data class GitHubChangedFile(
    val filename: String,
    val status: String,
    val additions: Int,
    val deletions: Int,
    val changes: Int,
    val patch: String? = null,
)

data class GitHubReview(
    val id: Long,
    val body: String,
)

data class GitHubReviewComment(
    val path: String,
    val line: Int,
    val side: String = "RIGHT",
    val body: String,
)

data class GitHubCreateReviewRequest(
    val ownerRepo: OwnerRepo,
    val pullNumber: Int,
    val commitId: String,
    val body: String,
    val comments: List<GitHubReviewComment>,
)

data class AiReviewResult(
    val verdict: String,
    val summary: String,
    val bugs: List<String> = emptyList(),
    val architectureProblems: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
    val findings: List<AiReviewFinding> = emptyList(),
)

data class AiReviewFinding(
    val category: String,
    val severity: String,
    val path: String?,
    val line: Int?,
    val body: String,
)

data class ValidatedReview(
    val result: AiReviewResult,
    val inlineComments: List<GitHubReviewComment>,
    val fallbackNotes: List<String>,
)

data class ReviewRagChunk(
    val source: String,
    val section: String,
    val chunkId: String,
    val text: String,
)

sealed interface ReviewRunResult {
    data object Published : ReviewRunResult
    data object Duplicate : ReviewRunResult
    data object Ignored : ReviewRunResult
}
