package com.sibgear.deepseek.chat.domain.model

data class TaskMemoryState(
    val goal: String? = null,
    val clarifiedFacts: List<String> = emptyList(),
    val constraints: List<String> = emptyList(),
    val terms: Map<String, String> = emptyMap(),
) {
    val isEmpty: Boolean
        get() = goal.isNullOrBlank() &&
            clarifiedFacts.isEmpty() &&
            constraints.isEmpty() &&
            terms.isEmpty()

    fun toRagQueryText(question: String): String {
        if (isEmpty) {
            return question
        }

        return buildString {
            appendLine("question: $question")
            appendLine()
            appendLine("task_memory:")
            goal?.takeIf { it.isNotBlank() }?.let {
                appendLine("goal: $it")
            }
            if (clarifiedFacts.isNotEmpty()) {
                appendLine("clarified_facts:")
                clarifiedFacts.forEach { appendLine("- $it") }
            }
            if (constraints.isNotEmpty()) {
                appendLine("constraints:")
                constraints.forEach { appendLine("- $it") }
            }
            if (terms.isNotEmpty()) {
                appendLine("terms:")
                terms.forEach { (term, meaning) -> appendLine("- $term = $meaning") }
            }
        }.trim()
    }
}
