package com.sibgear.aireview

object ReviewBodyFormatter {
    fun format(
        marker: String,
        deliveryId: String,
        review: ValidatedReview,
    ): String =
        buildString {
            appendLine(marker)
            appendLine("## AI Review")
            appendLine()
            appendLine("**Вердикт:** ${review.result.verdict.toVerdictText()}")
            appendLine()
            appendLine(review.result.summary.ifBlank { "Ревью завершено." })
            appendSection("Потенциальные баги", review.result.bugs)
            appendSection("Архитектурные проблемы", review.result.architectureProblems)
            appendSection("Рекомендации", review.result.recommendations)
            appendSection("Замечания без inline-привязки", review.fallbackNotes)
            if (deliveryId.isNotBlank()) {
                appendLine()
                appendLine("delivery: `$deliveryId`")
            }
        }.trimEnd()

    private fun StringBuilder.appendSection(title: String, items: List<String>) {
        appendLine()
        appendLine("### $title")
        if (items.isEmpty()) {
            appendLine("- Нет замечаний.")
        } else {
            items.forEach { appendLine("- $it") }
        }
    }

    private fun String.toVerdictText(): String =
        when (lowercase()) {
            "ok_to_merge", "ok", "approve" -> "всё хорошо, можно вливать"
            else -> "нужно доработать"
        }
}
