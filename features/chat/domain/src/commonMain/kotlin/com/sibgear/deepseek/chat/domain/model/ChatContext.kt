package com.sibgear.deepseek.chat.domain.model

data class ContextMessage(
    val role: ChatRole,
    val kind: ChatMessageKind = ChatMessageKind.Regular,
    val content: String,
)

data class ContextPlan(
    val apiMessages: List<ContextMessage>,
    val compressionRequest: CompressionRequest? = null,
    val stickyFactsUpdateRequest: StickyFactsUpdateRequest? = null,
)

data class CompressionRequest(
    val messages: List<ContextMessage>,
    val prompt: String,
)

data class StickyFact(
    val key: String,
    val value: String,
)

data class StickyFactsUpdateRequest(
    val messages: List<ContextMessage>,
    val prompt: String,
)

const val CompressionSummaryPrompt = """
Сожми предыдущий диалог для продолжения общения.
Это промежуточная компрессия контекста, а не финальное резюме.

Сохрани:
- цель пользователя и текущую задачу;
- важные решения, ограничения и предпочтения;
- факты, результаты, ошибки и открытые вопросы;
- следующий ожидаемый шаг.

Если в контексте уже есть предыдущее сжатие, обнови его новыми фактами без повторов.
Не добавляй ничего от себя.

Ответ дай кратко, списком.
"""

const val StickyFactsUpdatePrompt = """
Обнови sticky facts для продолжения диалога.

На входе есть текущие факты и последнее сообщение пользователя.
Верни только JSON object, без markdown и пояснений.
Ключи и значения должны быть короткими строками.
Если факт нужно удалить, верни для ключа null.
"""
