package com.sibgear.deepseek.chat.domain.model

data class AiRequestData(
    val systemPrompt: String,
    val prompt: String,
    val attachment: PromptAttachment? = null,
    val model: AiModel,
    val apiSettings: ApiSettings,
)

data class PromptAttachment(
    val fileName: String,
    val sizeBytes: Long,
    val content: String,
)

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
