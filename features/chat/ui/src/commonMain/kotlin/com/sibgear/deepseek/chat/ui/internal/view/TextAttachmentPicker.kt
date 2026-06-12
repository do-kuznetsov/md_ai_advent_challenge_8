package com.sibgear.deepseek.chat.ui.internal.view

import com.sibgear.deepseek.chat.domain.model.PromptAttachment
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

internal sealed interface TextAttachmentPickResult {
    data class Selected(val attachment: PromptAttachment) : TextAttachmentPickResult
    data class Error(val message: String) : TextAttachmentPickResult
}

internal fun pickTextAttachment(): TextAttachmentPickResult? {
    val chooser = JFileChooser().apply {
        dialogTitle = "Select text file"
        fileSelectionMode = JFileChooser.FILES_ONLY
        isMultiSelectionEnabled = false
        fileFilter = FileNameExtensionFilter("Text files", *TextFileExtensions)
    }

    if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
        return null
    }

    val file = chooser.selectedFile ?: return null
    if (!file.isFile) {
        return TextAttachmentPickResult.Error("Выбранный путь не является файлом.")
    }
    if (!file.isTextFile()) {
        return TextAttachmentPickResult.Error("Можно прикреплять только текстовые файлы.")
    }

    val content = runCatching { file.readUtf8Text() }
        .getOrElse { exception ->
            val message = when (exception) {
                is CharacterCodingException -> "Файл не удалось прочитать как UTF-8."
                else -> exception.message ?: "Не удалось прочитать файл."
            }
            return TextAttachmentPickResult.Error(message)
        }

    return TextAttachmentPickResult.Selected(
        PromptAttachment(
            fileName = file.name,
            sizeBytes = file.length(),
            content = content,
        ),
    )
}

private fun File.isTextFile(): Boolean {
    val extension = extension.lowercase()
    if (extension in TextFileExtensions) {
        return true
    }

    val contentType = runCatching { Files.probeContentType(toPath()) }.getOrNull()
    return contentType?.startsWith("text/") == true
}

private fun File.readUtf8Text(): String {
    val decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return inputStream().use { input ->
        InputStreamReader(input, decoder).readText()
    }
}

private val TextFileExtensions = arrayOf(
    "txt",
    "text",
    "md",
    "markdown",
    "json",
    "jsonl",
    "yaml",
    "yml",
    "xml",
    "csv",
    "tsv",
    "log",
    "kt",
    "kts",
    "java",
    "js",
    "jsx",
    "ts",
    "tsx",
    "html",
    "htm",
    "css",
    "scss",
    "properties",
    "toml",
    "ini",
    "gradle",
    "sql",
    "sh",
    "zsh",
    "bash",
)
