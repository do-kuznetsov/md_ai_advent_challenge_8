package com.sibgear.deepseek.chat.domain.model

const val MaxToolCallsPerInteraction = 30

const val ToolCallLimitExceededMessage =
    "Превышено количество MCP вызовов в рамках одного взаимодействия."

private const val MinimumToolCallReserve = 3

private val McpToolPolicy = """
[MCP_TOOL_POLICY]
- Лимит: максимум $MaxToolCallsPerInteraction MCP/file tool вызовов в рамках одного взаимодействия.
- Перед вызовами tools сначала составь минимальный план действий.
- Не повторяй одинаковые вызовы и не перечитывай файлы/ресурсы без новой причины.
- Используй поиск и чтение точечно, оставляй минимум $MinimumToolCallReserve вызова в запасе для проверки результата.
- Когда получишь предупреждение о малом остатке, заверши анализ и формируй ответ; вызывай tool только если без него нельзя ответить корректно.
""".trimIndent()

class AiToolCallBudget(
    private val maxCalls: Int = MaxToolCallsPerInteraction,
) {
    private var usedCalls: Int = 0
    private val warningThreshold: Int = maxOf((maxCalls + 9) / 10, MinimumToolCallReserve)

    val remainingCalls: Int
        get() = (maxCalls - usedCalls).coerceAtLeast(0)

    fun canExecuteBatch(size: Int): Boolean =
        size <= remainingCalls

    fun recordResult(result: AiToolResult): AiToolResult {
        usedCalls += 1
        return result.withBudgetWarningIfNeeded(
            remainingCalls = remainingCalls,
            maxCalls = maxCalls,
            warningThreshold = warningThreshold,
        )
    }
}

fun String.withMcpToolPolicy(
    hasTools: Boolean,
    warnings: List<String>,
): String = buildString {
    append(this@withMcpToolPolicy)
    if (hasTools) {
        appendSection(McpToolPolicy)
    }
    if (warnings.isNotEmpty()) {
        appendSection(
            buildString {
                appendLine("[MCP_WARNINGS]")
                warnings.forEach { warning ->
                    appendLine("- $warning")
                }
            }.trimEnd(),
        )
    }
}

private fun AiToolResult.withBudgetWarningIfNeeded(
    remainingCalls: Int,
    maxCalls: Int,
    warningThreshold: Int,
): AiToolResult {
    if (remainingCalls !in 1..warningThreshold) {
        return this
    }
    return copy(
        content = buildString {
            append(content)
            appendSection(
                """
                [MCP_TOOL_BUDGET_WARNING]
                Осталось $remainingCalls из $maxCalls MCP/file tool вызовов. Завершай работу с имеющимися данными или используй только критически необходимый tool call.
                """.trimIndent(),
            )
        },
    )
}

private fun StringBuilder.appendSection(section: String) {
    if (isNotBlank()) {
        appendLine()
        appendLine()
    }
    append(section)
}
