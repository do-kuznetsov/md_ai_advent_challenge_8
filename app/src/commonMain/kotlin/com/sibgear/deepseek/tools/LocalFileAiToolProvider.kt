package com.sibgear.deepseek.tools

import com.sibgear.deepseek.chat.domain.model.AiToolCatalog
import com.sibgear.deepseek.chat.domain.model.AiToolDefinition
import com.sibgear.deepseek.chat.domain.model.AiToolInvocation
import com.sibgear.deepseek.chat.domain.model.AiToolProvider
import com.sibgear.deepseek.chat.domain.model.AiToolResult
import com.sibgear.deepseek.chat.domain.model.AiToolSession
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

internal class LocalFileAiToolProvider(
    private val filesDir: File,
) : AiToolProvider {
    override suspend fun openSession(): AiToolSession =
        LocalFileAiToolSession(filesDir = filesDir)
}

private class LocalFileAiToolSession(
    private val filesDir: File,
) : AiToolSession {
    override val catalog: AiToolCatalog =
        AiToolCatalog(
            tools = listOf(LocalFileToolDefinition),
        )

    override suspend fun callTool(invocation: AiToolInvocation): AiToolResult {
        if (invocation.name != LocalFileToolName) {
            return AiToolResult(
                name = invocation.name,
                content = "Tool '${invocation.name}' не найден.",
                isError = true,
            )
        }

        val relativePath = invocation.arguments.stringArgument("relative_path")
            ?: return writeError("Параметр relative_path обязателен.")
        val content = invocation.arguments.stringArgument("content")
            ?: return writeError("Параметр content обязателен.")
        val overwrite = invocation.arguments.booleanArgument("overwrite") ?: false

        return writeFile(
            relativePath = relativePath,
            content = content,
            overwrite = overwrite,
        )
    }

    override suspend fun close() = Unit

    private fun writeFile(
        relativePath: String,
        content: String,
        overwrite: Boolean,
    ): AiToolResult =
        runCatching {
            val target = resolveTargetFile(relativePath)
                .getOrElse { throwable ->
                    return writeError(throwable.message ?: "Некорректный путь файла.")
                }
            val parent = target.parentFile
                ?: return writeError("Некорректный путь файла.")

            if (!parent.exists() && !parent.mkdirs()) {
                return writeError("Не удалось создать директорию: ${parent.absolutePath}")
            }
            if (target.exists() && target.isDirectory) {
                return writeError("Путь указывает на директорию: $relativePath")
            }
            if (target.exists() && !overwrite) {
                return writeError("Файл уже существует: $relativePath. Передайте overwrite=true для перезаписи.")
            }

            val bytes = content.toByteArray(StandardCharsets.UTF_8)
            target.writeBytes(bytes)
            AiToolResult(
                name = LocalFileToolName,
                content = "Файл сохранён: $relativePath (${bytes.size} bytes).",
            )
        }.getOrElse { throwable ->
            writeError("Не удалось сохранить файл: ${throwable.message ?: throwable::class.simpleName ?: "unknown"}")
        }

    private fun resolveTargetFile(relativePath: String): Result<File> {
        val trimmedPath = relativePath.trim()
        if (trimmedPath.isEmpty()) {
            return Result.failure(IllegalArgumentException("Параметр relative_path не должен быть пустым."))
        }
        if (File(trimmedPath).isAbsolute) {
            return Result.failure(IllegalArgumentException("Абсолютные пути запрещены. Используйте путь внутри files."))
        }

        if (!filesDir.exists() && !filesDir.mkdirs()) {
            return Result.failure(IllegalArgumentException("Не удалось создать директорию files: ${filesDir.absolutePath}"))
        }
        if (!filesDir.isDirectory) {
            return Result.failure(IllegalArgumentException("Путь files не является директорией: ${filesDir.absolutePath}"))
        }
        if (Files.isSymbolicLink(filesDir.toPath())) {
            return Result.failure(IllegalArgumentException("Директория files не должна быть символической ссылкой."))
        }

        val base = filesDir.canonicalFile
        val target = File(base, trimmedPath).canonicalFile
        val basePath = base.path
        val targetPath = target.path
        if (targetPath != basePath && !targetPath.startsWith(basePath + File.separator)) {
            return Result.failure(IllegalArgumentException("Путь выходит за пределы директории files."))
        }
        if (target == base) {
            return Result.failure(IllegalArgumentException("Путь должен указывать на файл внутри files."))
        }

        return Result.success(target)
    }

    private fun writeError(message: String): AiToolResult =
        AiToolResult(
            name = LocalFileToolName,
            content = "Ошибка записи локального файла: $message",
            isError = true,
        )
}

private fun JsonObject.stringArgument(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.booleanArgument(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.booleanOrNull

private const val LocalFileToolName = "write_local_file"

private val LocalFileToolDefinition = AiToolDefinition(
    name = LocalFileToolName,
    description = "Сохраняет UTF-8 файл локально на машине клиента только внутри директории files рядом с запущенным приложением.",
    parameters = JsonObject(
        mapOf(
            "type" to JsonPrimitive("object"),
            "properties" to JsonObject(
                mapOf(
                    "relative_path" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("Относительный путь файла внутри директории files."),
                        ),
                    ),
                    "content" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("Содержимое файла в UTF-8."),
                        ),
                    ),
                    "overwrite" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("boolean"),
                            "description" to JsonPrimitive("Перезаписать файл, если он уже существует. По умолчанию false."),
                        ),
                    ),
                ),
            ),
            "required" to JsonArray(
                listOf(
                    JsonPrimitive("relative_path"),
                    JsonPrimitive("content"),
                ),
            ),
            "additionalProperties" to JsonPrimitive(false),
        ),
    ),
)
