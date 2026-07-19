package com.sibgear.aireview

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface ReviewAssistant {
    suspend fun review(context: PullRequestReviewContext): AiReviewResult
}

class DeepSeekReviewAssistant(
    private val config: AiReviewConfig,
    private val client: HttpClient = defaultDeepSeekHttpClient(),
) : ReviewAssistant {
    private val apiBaseUrl = config.deepSeekBaseUrl.trimEnd('/')

    override suspend fun review(context: PullRequestReviewContext): AiReviewResult {
        val response = client.post("$apiBaseUrl/chat/completions") {
            bearerAuth(config.deepSeekApiKey)
            contentType(ContentType.Application.Json)
            setBody(
                DeepSeekChatCompletionRequest(
                    model = config.deepSeekModel,
                    messages = listOf(
                        DeepSeekApiMessage(role = "system", content = ReviewSystemPrompt),
                        DeepSeekApiMessage(role = "user", content = context.toReviewPrompt(config.maxPromptChars)),
                    ),
                    thinking = DeepSeekThinking(type = "enabled"),
                    reasoningEffort = "high",
                    maxTokens = 4096,
                    stream = false,
                ),
            )
        }
        if (!response.status.isSuccess()) {
            error("DeepSeek HTTP ${response.status.value}: ${response.bodyAsText().take(600)}")
        }
        val content = response.body<DeepSeekChatCompletionResponse>()
            .choices
            .firstOrNull()
            ?.message
            ?.content
            .orEmpty()
        return content.toAiReviewResult()
    }
}

private fun String.toAiReviewResult(): AiReviewResult {
    val jsonText = extractJsonObject()
    val dto = AiReviewJson.decodeFromString<AiReviewResultDto>(jsonText)
    return dto.toDomain()
}

private fun String.extractJsonObject(): String {
    val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```")
        .find(this)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
    if (!fenced.isNullOrBlank()) {
        return fenced
    }
    val start = indexOf('{')
    val end = lastIndexOf('}')
    require(start >= 0 && end > start) { "DeepSeek response does not contain JSON object." }
    return substring(start, end + 1)
}

private fun PullRequestReviewContext.toReviewPrompt(maxChars: Int): String {
    val prompt = buildString {
        appendLine("PR:")
        appendLine("- repo: ${ownerRepo.fullName}")
        appendLine("- number: $number")
        appendLine("- title: $title")
        appendLine("- head_sha: $headSha")
        appendLine("- base_sha: $baseSha")
        appendLine()
        appendLine("PR body:")
        appendLine(body.ifBlank { "(empty)" })
        appendLine()
        appendLine("Changed files:")
        changedFiles.forEach { file ->
            appendLine("- ${file.filename} (${file.status}, +${file.additions}/-${file.deletions}, changes=${file.changes})")
        }
        appendLine()
        appendLine("RAG documentation context:")
        if (ragChunks.isEmpty()) {
            appendLine("(no relevant documentation chunks)")
        } else {
            ragChunks.forEachIndexed { index, chunk ->
                appendLine("Chunk ${index + 1}: source=${chunk.source}; section=${chunk.section}; chunk_id=${chunk.chunkId}")
                appendLine(chunk.text)
                appendLine()
            }
        }
        appendLine()
        appendLine("Unified diff:")
        appendLine(diff)
    }
    return prompt.take(maxChars)
}

private fun defaultDeepSeekHttpClient(): HttpClient =
    HttpClient(io.ktor.client.engine.cio.CIO) {
        install(ContentNegotiation) {
            json(AiReviewJson)
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 30_000
            requestTimeoutMillis = 180_000
            socketTimeoutMillis = 180_000
        }
    }

@Serializable
private data class DeepSeekChatCompletionRequest(
    val model: String,
    val messages: List<DeepSeekApiMessage>,
    val thinking: DeepSeekThinking,
    @SerialName("reasoning_effort")
    val reasoningEffort: String,
    @SerialName("max_tokens")
    val maxTokens: Int,
    val stream: Boolean,
)

@Serializable
private data class DeepSeekApiMessage(
    val role: String,
    val content: String,
)

@Serializable
private data class DeepSeekThinking(
    val type: String,
)

@Serializable
private data class DeepSeekChatCompletionResponse(
    val choices: List<DeepSeekChoice> = emptyList(),
)

@Serializable
private data class DeepSeekChoice(
    val message: DeepSeekMessage? = null,
)

@Serializable
private data class DeepSeekMessage(
    val content: String? = null,
)

@Serializable
private data class AiReviewResultDto(
    val verdict: String = "needs_work",
    val summary: String = "",
    val bugs: List<String> = emptyList(),
    @SerialName("architecture_problems")
    val architectureProblems: List<String> = emptyList(),
    val recommendations: List<String> = emptyList(),
    val findings: List<AiReviewFindingDto> = emptyList(),
) {
    fun toDomain(): AiReviewResult =
        AiReviewResult(
            verdict = verdict,
            summary = summary,
            bugs = bugs,
            architectureProblems = architectureProblems,
            recommendations = recommendations,
            findings = findings.map { it.toDomain() },
        )
}

@Serializable
private data class AiReviewFindingDto(
    val category: String = "recommendation",
    val severity: String = "medium",
    val path: String? = null,
    val line: Int? = null,
    val body: String = "",
) {
    fun toDomain(): AiReviewFinding =
        AiReviewFinding(
            category = category,
            severity = severity,
            path = path,
            line = line,
            body = body,
        )
}

private const val ReviewSystemPrompt = """
You are a senior code reviewer.
Review only the pull request diff and provided project documentation context.
Focus on potential bugs, architectural problems, and actionable recommendations.
Do not invent project facts that are absent from RAG context or the changed files.
Return only a JSON object with this schema:
{
  "verdict": "ok_to_merge" | "needs_work",
  "summary": "short markdown summary",
  "bugs": ["potential bugs"],
  "architecture_problems": ["architecture problems"],
  "recommendations": ["recommendations"],
  "findings": [
    {
      "category": "bug" | "architecture" | "recommendation",
      "severity": "low" | "medium" | "high",
      "path": "relative/file/path",
      "line": 123,
      "body": "short actionable comment"
    }
  ]
}
Use findings only for changed lines from the diff. Put file-level or uncertain feedback into summary lists.
"""
