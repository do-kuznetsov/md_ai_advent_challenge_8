package com.sibgear.deepseek.tools

import com.sibgear.deepseek.chat.domain.model.AiToolInvocation
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalFileAiToolProviderTest {
    @Test
    fun disabledProjectPathYieldsNoFileToolsAndRejectsDirectCalls() = runBlocking {
        val provider = LocalFileAiToolProvider { null }

        val catalog = provider.availableTools()
        val result = provider.callTool(
            AiToolInvocation(
                name = "read_project_file",
                arguments = jsonArguments("relative_path" to "README.md"),
            ),
        )

        assertEquals(emptyList(), catalog.tools)
        assertTrue(catalog.warnings.single().contains("project path не задан"))
        assertTrue(result.isError)
    }

    @Test
    fun exposesProjectFileToolsWhenProjectPathIsValid() = runBlocking {
        val root = tempProjectDir()
        val provider = LocalFileAiToolProvider { root.absolutePath }

        val toolNames = provider.availableTools().tools.map { it.name }

        assertEquals(
            listOf(
                "read_project_file",
                "search_project_files",
                "write_project_file",
                "replace_in_project_file",
            ),
            toolNames,
        )
    }

    @Test
    fun readsUtf8FileInsideProjectRoot() = runBlocking {
        val root = tempProjectDir()
        File(root, "docs/readme.md").apply {
            parentFile.mkdirs()
            writeText("hello docs")
        }
        val provider = LocalFileAiToolProvider { root.absolutePath }

        val result = provider.callTool(
            AiToolInvocation(
                name = "read_project_file",
                arguments = jsonArguments("relative_path" to "docs/readme.md"),
            ),
        )

        assertFalse(result.isError)
        assertEquals("hello docs", result.content)
    }

    @Test
    fun searchesUtf8FilesInsideProjectRoot() = runBlocking {
        val root = tempProjectDir()
        File(root, "src/Main.kt").apply {
            parentFile.mkdirs()
            writeText("fun main() = println(\"Needle\")")
        }
        File(root, "docs/Guide.md").apply {
            parentFile.mkdirs()
            writeText("needle in docs")
        }
        val provider = LocalFileAiToolProvider { root.absolutePath }

        val result = provider.callTool(
            AiToolInvocation(
                name = "search_project_files",
                arguments = jsonArguments(
                    "query" to "needle",
                    "file_glob" to "**/*.kt",
                    "max_results" to 10,
                ),
            ),
        )

        assertFalse(result.isError)
        assertTrue(result.content.contains("src/Main.kt:1"))
        assertFalse(result.content.contains("Guide.md"))
    }

    @Test
    fun writesNestedFileInsideProjectRoot() = runBlocking {
        val root = tempProjectDir()
        val provider = LocalFileAiToolProvider { root.absolutePath }

        val result = provider.callTool(
            AiToolInvocation(
                name = "write_project_file",
                arguments = jsonArguments(
                    "relative_path" to "reports/2026/june.txt",
                    "content" to "nested",
                ),
            ),
        )

        assertFalse(result.isError)
        assertEquals("nested", File(root, "reports/2026/june.txt").readText())
    }

    @Test
    fun doesNotOverwriteExistingFileByDefault() = runBlocking {
        val root = tempProjectDir()
        val target = File(root, "report.txt")
        target.writeText("old")
        val provider = LocalFileAiToolProvider { root.absolutePath }

        val result = provider.callTool(
            AiToolInvocation(
                name = "write_project_file",
                arguments = jsonArguments(
                    "relative_path" to "report.txt",
                    "content" to "new",
                ),
            ),
        )

        assertTrue(result.isError)
        assertEquals("old", target.readText())
    }

    @Test
    fun overwritesExistingFileWhenAllowed() = runBlocking {
        val root = tempProjectDir()
        val target = File(root, "report.txt")
        target.writeText("old")
        val provider = LocalFileAiToolProvider { root.absolutePath }

        val result = provider.callTool(
            AiToolInvocation(
                name = "write_project_file",
                arguments = jsonArguments(
                    "relative_path" to "report.txt",
                    "content" to "new",
                    "overwrite" to true,
                ),
            ),
        )

        assertFalse(result.isError)
        assertEquals("new", target.readText())
    }

    @Test
    fun replacesExactTextInExistingFile() = runBlocking {
        val root = tempProjectDir()
        val target = File(root, "notes.txt")
        target.writeText("hello old")
        val provider = LocalFileAiToolProvider { root.absolutePath }

        val result = provider.callTool(
            AiToolInvocation(
                name = "replace_in_project_file",
                arguments = jsonArguments(
                    "relative_path" to "notes.txt",
                    "old_text" to "old",
                    "new_text" to "new",
                ),
            ),
        )

        assertFalse(result.isError)
        assertEquals("hello new", target.readText())
    }

    @Test
    fun replaceRejectsAmbiguousOldTextUnlessReplaceAll() = runBlocking {
        val root = tempProjectDir()
        val target = File(root, "notes.txt")
        target.writeText("old old")
        val provider = LocalFileAiToolProvider { root.absolutePath }

        val result = provider.callTool(
            AiToolInvocation(
                name = "replace_in_project_file",
                arguments = jsonArguments(
                    "relative_path" to "notes.txt",
                    "old_text" to "old",
                    "new_text" to "new",
                ),
            ),
        )

        assertTrue(result.isError)
        assertEquals("old old", target.readText())
    }

    @Test
    fun rejectsAbsolutePathAndPathTraversal() = runBlocking {
        val root = tempProjectDir()
        val outsideFile = File(root.parentFile, "outside.txt")
        val provider = LocalFileAiToolProvider { root.absolutePath }

        val absoluteResult = provider.callTool(
            AiToolInvocation(
                name = "write_project_file",
                arguments = jsonArguments(
                    "relative_path" to outsideFile.absolutePath,
                    "content" to "outside",
                ),
            ),
        )
        val traversalResult = provider.callTool(
            AiToolInvocation(
                name = "write_project_file",
                arguments = jsonArguments(
                    "relative_path" to "../outside.txt",
                    "content" to "outside",
                ),
            ),
        )

        assertTrue(absoluteResult.isError)
        assertTrue(traversalResult.isError)
        assertFalse(outsideFile.exists())
    }

    @Test
    fun rejectsSymlinkProjectRootAndSymlinkTraversal() = runBlocking {
        val realRoot = tempProjectDir()
        val linkRoot = Files.createTempDirectory("project-root-link-parent")
            .resolve("linked-root")
            .toFile()
        Files.createSymbolicLink(linkRoot.toPath(), realRoot.toPath())
        val symlinkRootProvider = LocalFileAiToolProvider { linkRoot.absolutePath }

        val insideLink = File(realRoot, "linked-file")
        Files.createSymbolicLink(insideLink.toPath(), File(realRoot.parentFile, "outside.txt").toPath())
        val provider = LocalFileAiToolProvider { realRoot.absolutePath }

        val catalog = symlinkRootProvider.availableTools()
        val result = provider.callTool(
            AiToolInvocation(
                name = "read_project_file",
                arguments = jsonArguments("relative_path" to "linked-file"),
            ),
        )

        assertEquals(emptyList(), catalog.tools)
        assertTrue(catalog.warnings.single().contains("символической ссылкой"))
        assertTrue(result.isError)
    }

    private fun jsonArguments(vararg pairs: Pair<String, Any>): JsonObject =
        JsonObject(
            pairs.associate { (key, value) ->
                key to value.toJsonElement()
            },
        )

    private fun Any.toJsonElement(): JsonElement =
        when (this) {
            is String -> JsonPrimitive(this)
            is Boolean -> JsonPrimitive(this)
            is Int -> JsonPrimitive(this)
            else -> error("Unsupported JSON test value: $this")
        }

    private fun tempProjectDir(): File =
        Files.createTempDirectory("project-file-tool")
            .toFile()
}
