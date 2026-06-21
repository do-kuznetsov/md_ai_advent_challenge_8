package com.sibgear.deepseek.assistant.memory.domain.model

data class AssistantInvariant(
    val id: String,
    val category: InvariantCategory,
    val statement: String,
    val rationale: String = "",
    val enabled: Boolean = true,
)

enum class InvariantCategory {
    Architecture,
    TechnicalDecision,
    StackConstraint,
    BusinessRule,
    Process,
    Security,
    Other,
}
