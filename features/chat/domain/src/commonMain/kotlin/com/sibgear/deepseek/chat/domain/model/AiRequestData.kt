package com.sibgear.deepseek.chat.domain.model

data class AiRequestData(
    val systemPrompt: String,
    val prompt: String,
    val attachment: PromptAttachment? = null,
    val model: AiModel,
    val apiSettings: ApiSettings,
    val contextManagementSettings: ContextManagementSettings = ContextManagementSettings(),
)

data class PromptAttachment(
    val fileName: String,
    val sizeBytes: Long,
    val content: String,
)

data class ContextManagementSettings(
    val mode: ContextManagementMode = ContextManagementMode.None,
    val summaryIntervalMessages: Int = DefaultContextManagementMessages,
    val slidingWindowMessages: Int = DefaultContextManagementMessages,
)

enum class ContextManagementMode {
    None,
    ContextSummary,
    SlidingWindow,
}

fun AiRequestData.userApiContent(): String? {
    val attachedFile = attachment ?: return null
    return buildString {
        append(prompt)
        appendLine()
        appendLine()
        appendLine("Attached text file: ${attachedFile.fileName} (${attachedFile.sizeBytes} bytes)")
        appendLine("```text")
        append(attachedFile.content)
        if (!attachedFile.content.endsWith('\n')) {
            appendLine()
        }
        append("```")
    }
}

const val DefaultContextManagementMessages = 10
