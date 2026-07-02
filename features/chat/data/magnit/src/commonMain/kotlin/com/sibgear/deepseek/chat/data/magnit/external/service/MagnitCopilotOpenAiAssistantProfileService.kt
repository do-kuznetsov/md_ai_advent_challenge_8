package com.sibgear.deepseek.chat.data.magnit.external.service

import com.sibgear.deepseek.assistant.memory.domain.model.AssistantInvariant
import com.sibgear.deepseek.assistant.memory.domain.model.InvariantCategory
import com.sibgear.deepseek.assistant.memory.domain.model.InvariantCollectionMessage
import com.sibgear.deepseek.assistant.memory.domain.model.InvariantCollectionRole
import com.sibgear.deepseek.assistant.memory.domain.model.UserProfile
import com.sibgear.deepseek.assistant.memory.domain.service.AssistantInvariantService
import com.sibgear.deepseek.assistant.memory.domain.service.AssistantProfileService
import com.sibgear.deepseek.chat.data.magnit.external.MagnitCopilotBaseUrl
import com.sibgear.deepseek.chat.data.magnit.external.MagnitCopilotProviderLabel
import com.sibgear.deepseek.chat.data.magnit.internal.mapper.toAssistantInvariants
import com.sibgear.deepseek.chat.data.magnit.internal.mapper.toUserProfile
import com.sibgear.deepseek.chat.data.magnit.internal.http.magnitCopilotHttpClient
import com.sibgear.deepseek.chat.data.magnit.internal.model.OpenRouterApiChatMessage
import com.sibgear.deepseek.chat.data.magnit.internal.model.OpenRouterApiErrorResponse
import com.sibgear.deepseek.chat.data.magnit.internal.model.OpenRouterChatCompletionRequest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal class MagnitCopilotOpenAiAssistantProfileService(
    private val apiKey: String,
    private val baseUrl: String = MagnitCopilotBaseUrl,
    private val providerLabel: String = MagnitCopilotProviderLabel,
) : AssistantProfileService,
    AssistantInvariantService {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val client = magnitCopilotHttpClient(
        json = json,
        connectTimeoutMillis = ConnectTimeoutMillis,
        socketTimeoutMillis = RequestTimeoutMillis,
        requestTimeoutMillis = RequestTimeoutMillis,
    )
    private val chatCompletionsUrl = "${baseUrl.trimEnd('/')}/chat/completions"

    override suspend fun updateProfile(
        currentProfile: UserProfile,
        interviewAnswers: List<String>,
        modelId: String,
    ): UserProfile {
        val response = client.post(chatCompletionsUrl) {
            bearerAuth(apiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                OpenRouterChatCompletionRequest(
                    model = modelId,
                    messages = listOf(
                        OpenRouterApiChatMessage(role = "user", content = profileUpdatePrompt(currentProfile, interviewAnswers)),
                    ),
                    stream = false,
                ),
            )
        }

        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error(formatApiError(response.status.value, response.status.description, body))
        }

        val content = json.decodeFromString<OpenRouterProfileResponse>(body)
            .choices
            .firstOrNull()
            ?.message
            ?.content
            ?.takeIf { it.isNotBlank() }
            ?: error("$providerLabel вернул пустой профиль.")

        return content.toUserProfile(json)
            ?: error("$providerLabel вернул профиль в неожиданном формате.")
    }

    override suspend fun updateInvariants(
        currentInvariants: List<AssistantInvariant>,
        chatMessages: List<InvariantCollectionMessage>,
        modelId: String,
    ): List<AssistantInvariant> {
        val response = client.post(chatCompletionsUrl) {
            bearerAuth(apiKey)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                OpenRouterChatCompletionRequest(
                    model = modelId,
                    messages = listOf(
                        OpenRouterApiChatMessage(
                            role = "user",
                            content = buildInvariantsUpdatePrompt(currentInvariants, chatMessages),
                        ),
                    ),
                    stream = false,
                ),
            )
        }

        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error(formatApiError(response.status.value, response.status.description, body))
        }

        val content = json.decodeFromString<OpenRouterProfileResponse>(body)
            .choices
            .firstOrNull()
            ?.message
            ?.content
            ?.takeIf { it.isNotBlank() }
            ?: error("$providerLabel вернул пустой список инвариантов.")

        return content.toAssistantInvariants(json)
            ?: error("$providerLabel вернул инварианты в неожиданном формате.")
    }

    private fun formatApiError(
        statusCode: Int,
        statusDescription: String,
        body: String,
    ): String {
        val apiMessage = runCatching {
            json.decodeFromString<OpenRouterApiErrorResponse>(body).error?.message
        }.getOrNull()

        val message = apiMessage ?: body.take(600).ifBlank { "без тела ответа" }
        return "Ошибка $providerLabel API: HTTP $statusCode $statusDescription\n$message"
    }
}

internal fun buildInvariantsUpdatePrompt(
    currentInvariants: List<AssistantInvariant>,
    chatMessages: List<InvariantCollectionMessage>,
): String =
    buildString {
        appendLine("Ты обновляешь список инвариантов проекта для код-ассистента.")
        appendLine("Инварианты - это жесткие правила, которые ассистент не должен нарушать.")
        appendLine("Верни только JSON object без markdown.")
        appendLine()
        appendLine("Schema:")
        appendLine(
            """{"invariants":[{"id":"invariant-1","category":"architecture","statement":"...","rationale":"...","enabled":true}]}""",
        )
        appendLine()
        appendLine("Allowed categories:")
        appendLine("architecture, technical_decision, stack_constraint, business_rule, process, security, other")
        appendLine()
        appendLine("Rules:")
        appendLine("- Сохрани существующие актуальные инварианты и их id.")
        appendLine("- Удали только явно отмененные или противоречащие новым ответам инварианты.")
        appendLine("- Добавляй только устойчивые обязательные правила, явно указанные пользователем в диалоге.")
        appendLine("- Вопросы ассистента в диалоге не являются фактами или инвариантами сами по себе.")
        appendLine("- Statement должен быть коротким, конкретным и проверяемым.")
        appendLine("- Не выдумывай факты и не добавляй ничего, чего нет в сообщениях пользователя.")
        appendLine()
        appendLine("Текущие инварианты:")
        if (currentInvariants.isEmpty()) {
            appendLine("- пусто")
        } else {
            currentInvariants.forEach { invariant ->
                appendLine(
                    "- id=${invariant.id}; category=${invariant.category.storageValue}; enabled=${invariant.enabled}; statement=${invariant.statement}; rationale=${invariant.rationale}",
                )
            }
        }
        appendLine()
        appendLine("Диалог сбора инвариантов:")
        if (chatMessages.isEmpty()) {
            appendLine("- пусто")
        } else {
            chatMessages.forEach { message ->
                appendLine("${message.role.promptLabel}: ${message.text.ifBlank { "пусто" }}")
            }
        }
    }

private val InvariantCollectionRole.promptLabel: String
    get() = when (this) {
        InvariantCollectionRole.Assistant -> "assistant"
        InvariantCollectionRole.User -> "user"
    }

private fun profileUpdatePrompt(
    currentProfile: UserProfile,
    interviewAnswers: List<String>,
): String =
    buildString {
        appendLine("Ты обновляешь профиль пользователя для персонализации ассистента.")
        appendLine("Верни только JSON object без markdown: {\"profile\":\"...\"}.")
        appendLine()
        appendLine("Профиль должен быть кратким свободным текстом на русском.")
        appendLine("Если пользователь указал другой язык общения, зафиксируй это как предпочтение в профиле.")
        appendLine("Сохрани только устойчивые факты для персонализации код-ассистента:")
        appendLine("- язык общения и форму обращения;")
        appendLine("- желаемую краткость или подробность ответов;")
        appendLine("- стиль общения и раздражающие паттерны;")
        appendLine("- роль пользователя, контекст проекта, продукт, аудиторию и текущую цель;")
        appendLine("- технические рамки: стек, архитектуру, сторонние зависимости, кодстайл, тесты, безопасность и процесс.")
        appendLine("Не выдумывай факты и не добавляй ничего, чего нет в ответах.")
        appendLine()
        appendLine("Текущий профиль:")
        appendLine(currentProfile.text.ifBlank { "пусто" })
        appendLine()
        appendLine("Ответы интервью:")
        interviewAnswers.forEachIndexed { index, answer ->
            appendLine("${index + 1}. ${answer.ifBlank { "нет ответа" }}")
        }
    }

private val InvariantCategory.storageValue: String
    get() = when (this) {
        InvariantCategory.Architecture -> "architecture"
        InvariantCategory.TechnicalDecision -> "technical_decision"
        InvariantCategory.StackConstraint -> "stack_constraint"
        InvariantCategory.BusinessRule -> "business_rule"
        InvariantCategory.Process -> "process"
        InvariantCategory.Security -> "security"
        InvariantCategory.Other -> "other"
    }

@Serializable
private data class OpenRouterProfileResponse(
    val choices: List<OpenRouterProfileChoice> = emptyList(),
)

@Serializable
private data class OpenRouterProfileChoice(
    val message: OpenRouterProfileMessage? = null,
)

@Serializable
private data class OpenRouterProfileMessage(
    val content: String? = null,
)

private const val ConnectTimeoutMillis = 30_000L
private const val RequestTimeoutMillis = 180_000L
