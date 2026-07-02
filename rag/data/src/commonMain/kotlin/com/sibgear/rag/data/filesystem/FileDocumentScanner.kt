package com.sibgear.rag.data.filesystem

import com.sibgear.rag.domain.model.DocumentScanResult
import com.sibgear.rag.domain.model.SourceDocument
import com.sibgear.rag.domain.repository.DocumentScanner
import java.io.File
import java.security.MessageDigest

class FileDocumentScanner : DocumentScanner {
    override suspend fun scan(inputPath: String): DocumentScanResult {
        val root = File(inputPath).canonicalFile
        val warnings = mutableListOf<String>()
        val documents = root.walkTopDown()
            .onEnter { directory -> directory.name !in ExcludedDirectories }
            .filter(File::isFile)
            .mapNotNull { file ->
                when {
                    file.extension.lowercase() == "pdf" -> {
                        warnings += "PDF skipped: ${file.relativeTo(root).invariantSeparatorsPath}"
                        null
                    }

                    !file.looksLikeText() -> {
                        warnings += "Binary skipped: ${file.relativeTo(root).invariantSeparatorsPath}"
                        null
                    }

                    else -> file.toSourceDocument(root, warnings)
                }
            }
            .toList()

        return DocumentScanResult(
            documents = documents,
            warnings = warnings,
        )
    }

    private fun File.toSourceDocument(
        root: File,
        warnings: MutableList<String>,
    ): SourceDocument? =
        runCatching {
            val text = readText(Charsets.UTF_8)
            val source = relativeTo(root).invariantSeparatorsPath
            if (text.isBlank()) {
                warnings += "Empty skipped: $source"
                null
            } else {
                SourceDocument(
                    source = source,
                    title = name,
                    text = text,
                    contentSha256 = text.sha256(),
                )
            }
        }.getOrElse { error ->
            warnings += "Unreadable skipped: ${relativeTo(root).invariantSeparatorsPath} (${error.message})"
            null
        }

    private fun File.looksLikeText(): Boolean {
        if (extension.lowercase() !in TextExtensions) {
            return false
        }
        val bytes = inputStream().use { input -> input.readNBytes(4096) }
        return bytes.none { it.toInt() == 0 }
    }

    private fun String.sha256(): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        val ExcludedDirectories = setOf(".git", ".gradle", ".idea", "build")
        val TextExtensions = setOf(
            "txt",
            "md",
            "markdown",
            "kt",
            "kts",
            "java",
            "xml",
            "json",
            "yaml",
            "yml",
            "toml",
            "properties",
            "gradle",
            "csv",
            "html",
            "css",
            "js",
            "ts",
            "tsx",
            "jsx",
            "py",
            "c",
            "cpp",
            "h",
            "hpp",
            "swift",
            "rs",
            "go",
            "sql",
        )
    }
}
