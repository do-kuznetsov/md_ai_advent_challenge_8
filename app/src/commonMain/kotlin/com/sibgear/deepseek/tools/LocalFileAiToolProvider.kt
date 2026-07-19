package com.sibgear.deepseek.tools

import com.sibgear.deepseek.chat.domain.model.AiToolCatalog
import com.sibgear.deepseek.chat.domain.model.AiToolDefinition
import com.sibgear.deepseek.chat.domain.model.AiToolInvocation
import com.sibgear.deepseek.chat.domain.model.AiToolProvider
import com.sibgear.deepseek.chat.domain.model.AiToolResult
import com.sibgear.deepseek.chat.domain.model.AiToolSession
import java.io.File
import java.nio.charset.MalformedInputException
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

internal class LocalFileAiToolProvider(
    private val projectRootProvider: () -> String?,
) : AiToolProvider {
    override suspend fun openSession(): AiToolSession =
        LocalFileAiToolSession(projectRootProvider = projectRootProvider)
}

private class LocalFileAiToolSession(
    projectRootProvider: () -> String?,
) : AiToolSession {
    private val rootResult = resolveProjectRoot(projectRootProvider())
    private val root = rootResult.getOrNull()

    override val catalog: AiToolCatalog =
        AiToolCatalog(
            tools = if (root != null) ProjectFileToolDefinitions else emptyList(),
            warnings = rootResult.exceptionOrNull()?.message?.let { warning ->
                listOf("Project file tools disabled: $warning")
            }.orEmpty(),
        )

    override suspend fun callTool(invocation: AiToolInvocation): AiToolResult {
        val projectRoot = root
            ?: return invocation.fileToolError(rootResult.exceptionOrNull()?.message ?: "project path не задан.")

        return when (invocation.name) {
            ReadProjectFileToolName -> readProjectFile(projectRoot, invocation.arguments)
            SearchProjectFilesToolName -> searchProjectFiles(projectRoot, invocation.arguments)
            WriteProjectFileToolName -> writeProjectFile(projectRoot, invocation.arguments)
            ReplaceInProjectFileToolName -> replaceInProjectFile(projectRoot, invocation.arguments)
            else -> AiToolResult(
                name = invocation.name,
                content = "Tool '${invocation.name}' не найден.",
                isError = true,
            )
        }
    }

    override suspend fun close() = Unit
}

private fun readProjectFile(
    root: File,
    arguments: JsonObject,
): AiToolResult {
    val relativePath = arguments.stringArgument("relative_path")
        ?: return ReadProjectFileToolName.fileToolError("Параметр relative_path обязателен.")
    val target = resolveExistingFile(root, relativePath)
        .getOrElse { return ReadProjectFileToolName.fileToolError(it.message ?: "Некорректный путь файла.") }

    return runCatching {
        AiToolResult(
            name = ReadProjectFileToolName,
            content = target.readText(StandardCharsets.UTF_8),
        )
    }.getOrElse {
        ReadProjectFileToolName.fileToolError("Не удалось прочитать UTF-8 файл: ${it.safeMessage()}")
    }
}

private fun searchProjectFiles(
    root: File,
    arguments: JsonObject,
): AiToolResult {
    val query = arguments.stringArgument("query")?.takeIf { it.isNotBlank() }
        ?: return SearchProjectFilesToolName.fileToolError("Параметр query обязателен.")
    val fileGlob = arguments.stringArgument("file_glob")?.takeIf { it.isNotBlank() }
    val maxResults = arguments.intArgument("max_results")?.coerceIn(1, MaxSearchResults) ?: DefaultSearchResults
    val matcher = fileGlob?.let { glob ->
        runCatching { FileSystems.getDefault().getPathMatcher("glob:$glob") }
            .getOrElse { return SearchProjectFilesToolName.fileToolError("Некорректный file_glob: ${it.safeMessage()}") }
    }
    val rootPath = root.toPath()
    val matches = mutableListOf<String>()

    root.walkTopDown()
        .onEnter { directory -> directory.shouldEnterSearchDirectory(root) }
        .forEach { file ->
            if (matches.size >= maxResults || !file.isFile || Files.isSymbolicLink(file.toPath())) {
                return@forEach
            }
            val relative = rootPath.relativize(file.toPath()).toString()
            if (matcher != null && !matcher.matches(FileSystems.getDefault().getPath(relative))) {
                return@forEach
            }
            val text = file.readUtf8OrNull() ?: return@forEach
            text.lineSequence().forEachIndexed { index, line ->
                if (matches.size >= maxResults) {
                    return@forEachIndexed
                }
                if (line.contains(query, ignoreCase = true)) {
                    matches += "$relative:${index + 1}: ${line.trim().take(MaxSearchLineChars)}"
                }
            }
        }

    return AiToolResult(
        name = SearchProjectFilesToolName,
        content = matches.joinToString(separator = "\n").ifBlank { "Совпадений не найдено." },
    )
}

private fun writeProjectFile(
    root: File,
    arguments: JsonObject,
): AiToolResult {
    val relativePath = arguments.stringArgument("relative_path")
        ?: return WriteProjectFileToolName.fileToolError("Параметр relative_path обязателен.")
    val content = arguments.stringArgument("content")
        ?: return WriteProjectFileToolName.fileToolError("Параметр content обязателен.")
    val overwrite = arguments.booleanArgument("overwrite") ?: false
    val target = resolveTargetFile(root, relativePath, mustExist = false)
        .getOrElse { return WriteProjectFileToolName.fileToolError(it.message ?: "Некорректный путь файла.") }

    return runCatching {
        val parent = target.parentFile
            ?: return WriteProjectFileToolName.fileToolError("Некорректный путь файла.")
        if (parent.exists() && !parent.isDirectory) {
            return WriteProjectFileToolName.fileToolError("Родительский путь не является директорией: ${parent.name}")
        }
        if (!parent.exists() && !parent.mkdirs()) {
            return WriteProjectFileToolName.fileToolError("Не удалось создать директорию: ${parent.absolutePath}")
        }
        validateNoSymlinkTraversal(root, target)
            .getOrElse { return WriteProjectFileToolName.fileToolError(it.message ?: "Некорректный путь файла.") }
        if (target.exists() && target.isDirectory) {
            return WriteProjectFileToolName.fileToolError("Путь указывает на директорию: $relativePath")
        }
        if (target.exists() && !overwrite) {
            return WriteProjectFileToolName.fileToolError(
                "Файл уже существует: $relativePath. Передайте overwrite=true для перезаписи.",
            )
        }

        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        target.writeBytes(bytes)
        AiToolResult(
            name = WriteProjectFileToolName,
            content = "Файл сохранён: $relativePath (${bytes.size} bytes).",
        )
    }.getOrElse {
        WriteProjectFileToolName.fileToolError("Не удалось сохранить файл: ${it.safeMessage()}")
    }
}

private fun replaceInProjectFile(
    root: File,
    arguments: JsonObject,
): AiToolResult {
    val relativePath = arguments.stringArgument("relative_path")
        ?: return ReplaceInProjectFileToolName.fileToolError("Параметр relative_path обязателен.")
    val oldText = arguments.stringArgument("old_text")
        ?: return ReplaceInProjectFileToolName.fileToolError("Параметр old_text обязателен.")
    val newText = arguments.stringArgument("new_text")
        ?: return ReplaceInProjectFileToolName.fileToolError("Параметр new_text обязателен.")
    val replaceAll = arguments.booleanArgument("replace_all") ?: false
    if (oldText.isEmpty()) {
        return ReplaceInProjectFileToolName.fileToolError("Параметр old_text не должен быть пустым.")
    }
    val target = resolveExistingFile(root, relativePath)
        .getOrElse { return ReplaceInProjectFileToolName.fileToolError(it.message ?: "Некорректный путь файла.") }

    return runCatching {
        val text = target.readText(StandardCharsets.UTF_8)
        val occurrences = text.split(oldText).size - 1
        when {
            occurrences == 0 -> {
                return ReplaceInProjectFileToolName.fileToolError("old_text не найден в файле: $relativePath")
            }
            occurrences > 1 && !replaceAll -> {
                return ReplaceInProjectFileToolName.fileToolError(
                    "old_text найден $occurrences раз. Передайте replace_all=true или уточните фрагмент.",
                )
            }
        }
        val updated = if (replaceAll) {
            text.replace(oldText, newText)
        } else {
            text.replaceFirst(oldText, newText)
        }
        target.writeText(updated, StandardCharsets.UTF_8)
        AiToolResult(
            name = ReplaceInProjectFileToolName,
            content = "Файл обновлён: $relativePath ($occurrences replacements matched).",
        )
    }.getOrElse {
        ReplaceInProjectFileToolName.fileToolError("Не удалось изменить UTF-8 файл: ${it.safeMessage()}")
    }
}

private fun resolveProjectRoot(rawRoot: String?): Result<File> {
    val trimmedRoot = rawRoot?.trim().orEmpty()
    if (trimmedRoot.isEmpty()) {
        return Result.failure(IllegalStateException("project path не задан. File tools отключены."))
    }
    val root = File(trimmedRoot)
    if (!root.exists()) {
        return Result.failure(IllegalArgumentException("project path не существует: $trimmedRoot"))
    }
    if (!root.isDirectory) {
        return Result.failure(IllegalArgumentException("project path не является директорией: $trimmedRoot"))
    }
    if (Files.isSymbolicLink(root.toPath())) {
        return Result.failure(IllegalArgumentException("project path не должен быть символической ссылкой."))
    }
    return Result.success(root.canonicalFile)
}

private fun resolveExistingFile(
    root: File,
    relativePath: String,
): Result<File> =
    resolveTargetFile(root, relativePath, mustExist = true).fold(
        onSuccess = { target ->
            when {
                !target.exists() -> Result.failure(IllegalArgumentException("Файл не найден: $relativePath"))
                target.isDirectory -> Result.failure(IllegalArgumentException("Путь указывает на директорию: $relativePath"))
                !target.toPath().isRegularFile() -> {
                    Result.failure(IllegalArgumentException("Путь не является обычным файлом: $relativePath"))
                }
                else -> Result.success(target)
            }
        },
        onFailure = { Result.failure(it) },
    )

private fun resolveTargetFile(
    root: File,
    relativePath: String,
    mustExist: Boolean,
): Result<File> {
    val trimmedPath = relativePath.trim()
    if (trimmedPath.isEmpty()) {
        return Result.failure(IllegalArgumentException("Параметр relative_path не должен быть пустым."))
    }
    val rawPath = File(trimmedPath)
    if (rawPath.isAbsolute) {
        return Result.failure(IllegalArgumentException("Абсолютные пути запрещены. Используйте путь внутри project path."))
    }
    if (Path.of(trimmedPath).any { it.name == ".." }) {
        return Result.failure(IllegalArgumentException("Переходы через '..' запрещены."))
    }

    val target = if (mustExist) {
        File(root, trimmedPath).canonicalFile
    } else {
        File(root, trimmedPath).absoluteFile.normalizeLexically()
    }
    val rootPath = root.canonicalFile.toPath()
    val targetPath = target.toPath().toAbsolutePath().normalize()
    if (targetPath == rootPath) {
        return Result.failure(IllegalArgumentException("Путь должен указывать на файл внутри project path."))
    }
    if (!targetPath.startsWith(rootPath)) {
        return Result.failure(IllegalArgumentException("Путь выходит за пределы project path."))
    }
    return validateNoSymlinkTraversal(root, target).map { target }
}

private fun validateNoSymlinkTraversal(
    root: File,
    target: File,
): Result<Unit> {
    val rootPath = root.canonicalFile.toPath()
    val targetPath = target.toPath().toAbsolutePath().normalize()
    val relative = rootPath.relativize(targetPath)
    var current = rootPath
    relative.forEach { segment ->
        current = current.resolve(segment)
        if (Files.exists(current) && Files.isSymbolicLink(current)) {
            return Result.failure(IllegalArgumentException("Symbolic links внутри project path запрещены: $segment"))
        }
    }
    return Result.success(Unit)
}

private fun File.normalizeLexically(): File =
    toPath().normalize().toFile()

private fun File.shouldEnterSearchDirectory(root: File): Boolean {
    if (this == root) {
        return true
    }
    if (Files.isSymbolicLink(toPath())) {
        return false
    }
    return name !in SkippedSearchDirectories
}

private fun File.readUtf8OrNull(): String? =
    try {
        readText(StandardCharsets.UTF_8)
    } catch (_: MalformedInputException) {
        null
    } catch (_: Throwable) {
        null
    }

private fun AiToolInvocation.fileToolError(message: String): AiToolResult =
    name.fileToolError(message)

private fun String.fileToolError(message: String): AiToolResult =
    AiToolResult(
        name = this,
        content = "Ошибка файлового tool: $message",
        isError = true,
    )

private fun Throwable.safeMessage(): String =
    message ?: this::class.simpleName ?: "unknown"

private fun JsonObject.stringArgument(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.booleanArgument(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.booleanOrNull

private fun JsonObject.intArgument(name: String): Int? =
    (this[name] as? JsonPrimitive)?.intOrNull

private const val ReadProjectFileToolName = "read_project_file"
private const val SearchProjectFilesToolName = "search_project_files"
private const val WriteProjectFileToolName = "write_project_file"
private const val ReplaceInProjectFileToolName = "replace_in_project_file"
private const val DefaultSearchResults = 50
private const val MaxSearchResults = 200
private const val MaxSearchLineChars = 240

private val SkippedSearchDirectories = setOf(
    ".git",
    ".gradle",
    "build",
)

private val ProjectFileToolDefinitions = listOf(
    AiToolDefinition(
        name = ReadProjectFileToolName,
        description = "Читает UTF-8 файл внутри зафиксированного project path. Запрещены absolute paths, '..' и symlinks.",
        parameters = JsonObject(
            mapOf(
                "type" to JsonPrimitive("object"),
                "properties" to JsonObject(
                    mapOf(
                        "relative_path" to JsonObject(
                            mapOf(
                                "type" to JsonPrimitive("string"),
                                "description" to JsonPrimitive("Относительный путь файла внутри project path."),
                            ),
                        ),
                    ),
                ),
                "required" to JsonArray(listOf(JsonPrimitive("relative_path"))),
                "additionalProperties" to JsonPrimitive(false),
            ),
        ),
    ),
    AiToolDefinition(
        name = SearchProjectFilesToolName,
        description = "Ищет текст по UTF-8 файлам внутри зафиксированного project path. Возвращает path:line:snippet.",
        parameters = JsonObject(
            mapOf(
                "type" to JsonPrimitive("object"),
                "properties" to JsonObject(
                    mapOf(
                        "query" to JsonObject(
                            mapOf(
                                "type" to JsonPrimitive("string"),
                                "description" to JsonPrimitive("Текст для поиска."),
                            ),
                        ),
                        "file_glob" to JsonObject(
                            mapOf(
                                "type" to JsonPrimitive("string"),
                                "description" to JsonPrimitive("Необязательный glob по относительному пути, например **/*.kt."),
                            ),
                        ),
                        "max_results" to JsonObject(
                            mapOf(
                                "type" to JsonPrimitive("integer"),
                                "description" to JsonPrimitive("Максимум результатов, 1..200. По умолчанию 50."),
                            ),
                        ),
                    ),
                ),
                "required" to JsonArray(listOf(JsonPrimitive("query"))),
                "additionalProperties" to JsonPrimitive(false),
            ),
        ),
    ),
    AiToolDefinition(
        name = WriteProjectFileToolName,
        description = "Создает или перезаписывает UTF-8 файл внутри зафиксированного project path.",
        parameters = JsonObject(
            mapOf(
                "type" to JsonPrimitive("object"),
                "properties" to JsonObject(
                    mapOf(
                        "relative_path" to JsonObject(
                            mapOf(
                                "type" to JsonPrimitive("string"),
                                "description" to JsonPrimitive("Относительный путь файла внутри project path."),
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
                "required" to JsonArray(listOf(JsonPrimitive("relative_path"), JsonPrimitive("content"))),
                "additionalProperties" to JsonPrimitive(false),
            ),
        ),
    ),
    AiToolDefinition(
        name = ReplaceInProjectFileToolName,
        description = "Изменяет UTF-8 файл внутри project path через точную замену old_text на new_text.",
        parameters = JsonObject(
            mapOf(
                "type" to JsonPrimitive("object"),
                "properties" to JsonObject(
                    mapOf(
                        "relative_path" to JsonObject(
                            mapOf(
                                "type" to JsonPrimitive("string"),
                                "description" to JsonPrimitive("Относительный путь файла внутри project path."),
                            ),
                        ),
                        "old_text" to JsonObject(
                            mapOf(
                                "type" to JsonPrimitive("string"),
                                "description" to JsonPrimitive("Точный фрагмент для замены."),
                            ),
                        ),
                        "new_text" to JsonObject(
                            mapOf(
                                "type" to JsonPrimitive("string"),
                                "description" to JsonPrimitive("Новый фрагмент."),
                            ),
                        ),
                        "replace_all" to JsonObject(
                            mapOf(
                                "type" to JsonPrimitive("boolean"),
                                "description" to JsonPrimitive("Заменить все вхождения. По умолчанию false."),
                            ),
                        ),
                    ),
                ),
                "required" to JsonArray(
                    listOf(
                        JsonPrimitive("relative_path"),
                        JsonPrimitive("old_text"),
                        JsonPrimitive("new_text"),
                    ),
                ),
                "additionalProperties" to JsonPrimitive(false),
            ),
        ),
    ),
)
