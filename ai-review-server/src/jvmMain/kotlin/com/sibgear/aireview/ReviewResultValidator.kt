package com.sibgear.aireview

class ReviewResultValidator(
    private val maxInlineComments: Int,
) {
    fun validate(
        result: AiReviewResult,
        changedLinesByPath: Map<String, Set<Int>>,
    ): ValidatedReview {
        val inlineComments = mutableListOf<GitHubReviewComment>()
        val fallbackNotes = mutableListOf<String>()

        result.findings.forEach { finding ->
            val path = finding.path
            val line = finding.line
            val body = finding.body.trim()
            val canComment = path != null &&
                line != null &&
                body.isNotBlank() &&
                changedLinesByPath[path]?.contains(line) == true &&
                inlineComments.size < maxInlineComments
            if (canComment) {
                inlineComments += GitHubReviewComment(
                    path = path,
                    line = line,
                    body = "[${finding.category}/${finding.severity}] $body",
                )
            } else if (body.isNotBlank()) {
                fallbackNotes += buildString {
                    if (path != null && line != null) {
                        append("`$path:$line` ")
                    }
                    append("[${finding.category}/${finding.severity}] ")
                    append(body)
                }
            }
        }

        return ValidatedReview(
            result = result,
            inlineComments = inlineComments,
            fallbackNotes = fallbackNotes,
        )
    }
}
