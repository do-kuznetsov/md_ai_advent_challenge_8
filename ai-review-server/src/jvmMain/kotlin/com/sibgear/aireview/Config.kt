package com.sibgear.aireview

import com.sibgear.aireview.generated.GeneratedAiReviewSecrets

data class AiReviewConfig(
    val host: String = env("AI_REVIEW_HOST") ?: "127.0.0.1",
    val port: Int = env("AI_REVIEW_PORT")?.toIntOrNull() ?: 19090,
    val githubApiBaseUrl: String = env("GITHUB_API_BASE_URL") ?: "https://api.github.com",
    val githubToken: String = env("GITHUB_TOKEN") ?: GeneratedAiReviewSecrets.GitHubToken,
    val githubWebhookSecret: String = env("GITHUB_WEBHOOK_SECRET") ?: GeneratedAiReviewSecrets.GitHubWebhookSecret,
    val githubAllowedRepo: String = env("GITHUB_ALLOWED_REPO") ?: GeneratedAiReviewSecrets.GitHubAllowedRepo,
    val deepSeekApiKey: String = env("DEEPSEEK_API_KEY") ?: GeneratedAiReviewSecrets.DeepSeekApiKey,
    val deepSeekBaseUrl: String = env("DEEPSEEK_BASE_URL") ?: "https://api.deepseek.com",
    val deepSeekModel: String = env("DEEPSEEK_MODEL") ?: "deepseek-v4-pro",
    val ragIndexDirectory: String = env("RAG_INDEX_DIR") ?: "rag/indexed",
    val embeddingModelDirectory: String = env("EMBEDDING_MODEL_DIR") ?: "rag/models/nomic-embed-text",
    val maxFiles: Int = env("AI_REVIEW_MAX_FILES")?.toIntOrNull() ?: 50,
    val maxPromptChars: Int = env("AI_REVIEW_MAX_PROMPT_CHARS")?.toIntOrNull() ?: 200_000,
    val maxInlineComments: Int = env("AI_REVIEW_MAX_INLINE_COMMENTS")?.toIntOrNull() ?: 20,
) {
    fun requireReady() {
        require(githubToken.isNotBlank()) { "github_token is required in .keys.txt or GITHUB_TOKEN." }
        require(githubWebhookSecret.isNotBlank()) {
            "github_webhook_secret is required in .keys.txt or GITHUB_WEBHOOK_SECRET."
        }
        require(githubAllowedRepo.isNotBlank()) {
            "github_allowed_repo is required in .keys.txt or GITHUB_ALLOWED_REPO."
        }
        require(deepSeekApiKey.isNotBlank()) { "deepseek_api_key is required in .keys.txt or DEEPSEEK_API_KEY." }
    }
}

private fun env(name: String): String? =
    System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }
