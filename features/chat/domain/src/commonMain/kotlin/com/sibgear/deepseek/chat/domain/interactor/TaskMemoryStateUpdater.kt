package com.sibgear.deepseek.chat.domain.interactor

import com.sibgear.deepseek.chat.domain.model.ChatMessage
import com.sibgear.deepseek.chat.domain.model.ChatMessageKind
import com.sibgear.deepseek.chat.domain.model.ChatRole
import com.sibgear.deepseek.chat.domain.model.TaskMemoryState

class TaskMemoryStateUpdater {
    fun replay(messages: List<ChatMessage>): TaskMemoryState =
        messages.fold(TaskMemoryState()) { state, message ->
            if (message.role == ChatRole.User && message.kind == ChatMessageKind.Regular) {
                update(state, message.content)
            } else {
                state
            }
        }

    fun update(
        state: TaskMemoryState,
        userMessage: String,
    ): TaskMemoryState {
        val normalized = userMessage.normalizeMemoryText()
        if (normalized.isBlank()) {
            return state
        }

        val extractedTerms = extractTerms(normalized)
        val extractedConstraints = extractConstraints(normalized)
        val extractedFacts = extractClarifiedFact(normalized, extractedTerms.keys, extractedConstraints)
        val extractedGoal = extractGoal(normalized)

        return TaskMemoryState(
            goal = extractedGoal ?: state.goal,
            clarifiedFacts = state.clarifiedFacts.plusDistinct(extractedFacts),
            constraints = state.constraints.plusDistinct(extractedConstraints),
            terms = state.terms + extractedTerms,
        )
    }

    private fun extractGoal(text: String): String? {
        val explicitGoal = GoalRegex.find(text)?.groupValues?.getOrNull(2)?.cleanMemoryValue()
        if (!explicitGoal.isNullOrBlank()) {
            return explicitGoal
        }

        return NeedRegex.find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.cleanMemoryValue()
            ?.takeIf { it.isNotBlank() && !it.endsWith("?") }
    }

    private fun extractTerms(text: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        TermRegex.findAll(text).forEach { match ->
            val term = match.groupValues.getOrNull(1)?.cleanMemoryValue()?.trim('`')
            val meaning = match.groupValues.getOrNull(2)?.cleanMemoryValue()?.trim('`')
            if (!term.isNullOrBlank() && !meaning.isNullOrBlank()) {
                result[term] = meaning
            }
        }
        return result
    }

    private fun extractConstraints(text: String): List<String> {
        val lower = text.lowercase()
        if (ConstraintMarkers.none { it in lower }) {
            return emptyList()
        }
        return listOf(text.cleanMemoryValue())
    }

    private fun extractClarifiedFact(
        text: String,
        termNames: Set<String>,
        constraints: List<String>,
    ): List<String> {
        if (text.endsWith("?") || constraints.contains(text.cleanMemoryValue())) {
            return emptyList()
        }

        val lower = text.lowercase()
        if (FactMarkers.none { it in lower } && termNames.isEmpty()) {
            return emptyList()
        }

        return listOf(text.cleanMemoryValue())
    }

    private companion object {
        val GoalRegex = Regex(
            pattern = "(цель|задача)\\s*[:\\-–—]?\\s*(.+)",
            options = setOf(RegexOption.IGNORE_CASE),
        )
        val NeedRegex = Regex(
            pattern = "(?:нужно|надо|хочу|планирую)\\s+(.+)",
            options = setOf(RegexOption.IGNORE_CASE),
        )
        val TermRegex = Regex(
            pattern = "(?:термин|называем|будем называть)\\s*[:\\-–—]?\\s*`?([^=`:]+?)`?\\s*(?:=|это|—|-)\\s*`?(.+?)`?(?:$|[.;])",
            options = setOf(RegexOption.IGNORE_CASE),
        )
        val ConstraintMarkers = listOf(
            "огранич",
            "нельзя",
            "запрещ",
            "без ",
            "только ",
            "не нужно",
            "не надо",
            "обязательно",
            "должен",
        )
        val FactMarkers = listOf(
            "модул",
            "у нас",
            "есть",
            "используем",
            "сейчас",
            "подготов",
            "уточн",
            "feature",
            "presentation",
            "data",
            "domain",
        )
    }
}

private fun String.normalizeMemoryText(): String =
    lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" ")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun String.cleanMemoryValue(): String =
    trim()
        .trim('.', ';')
        .trim()

private fun List<String>.plusDistinct(values: List<String>): List<String> {
    val result = toMutableList()
    values.forEach { value ->
        if (value.isNotBlank() && result.none { it.equals(value, ignoreCase = true) }) {
            result += value
        }
    }
    return result
}
