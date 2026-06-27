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
    fun writesFileInsideFilesDirectory() = runBlocking {
        val filesDir = tempFilesDir()
        val provider = LocalFileAiToolProvider(filesDir)

        val result = provider.callTool(
            AiToolInvocation(
                name = "write_local_file",
                arguments = arguments(
                    relativePath = "report.txt",
                    content = "hello",
                ),
            ),
        )

        assertFalse(result.isError)
        assertEquals("hello", File(filesDir, "report.txt").readText())
    }

    @Test
    fun createsNestedDirectoriesInsideFilesDirectory() = runBlocking {
        val filesDir = tempFilesDir()
        val provider = LocalFileAiToolProvider(filesDir)

        val result = provider.callTool(
            AiToolInvocation(
                name = "write_local_file",
                arguments = arguments(
                    relativePath = "reports/2026/june.txt",
                    content = "nested",
                ),
            ),
        )

        assertFalse(result.isError)
        assertEquals("nested", File(filesDir, "reports/2026/june.txt").readText())
    }

    @Test
    fun rejectsAbsolutePath() = runBlocking {
        val filesDir = tempFilesDir()
        val outsideFile = File(filesDir.parentFile, "outside.txt")
        val provider = LocalFileAiToolProvider(filesDir)

        val result = provider.callTool(
            AiToolInvocation(
                name = "write_local_file",
                arguments = arguments(
                    relativePath = outsideFile.absolutePath,
                    content = "outside",
                ),
            ),
        )

        assertTrue(result.isError)
        assertFalse(outsideFile.exists())
    }

    @Test
    fun rejectsPathTraversal() = runBlocking {
        val filesDir = tempFilesDir()
        val outsideFile = File(filesDir.parentFile, "outside.txt")
        val provider = LocalFileAiToolProvider(filesDir)

        val result = provider.callTool(
            AiToolInvocation(
                name = "write_local_file",
                arguments = arguments(
                    relativePath = "../outside.txt",
                    content = "outside",
                ),
            ),
        )

        assertTrue(result.isError)
        assertFalse(outsideFile.exists())
    }

    @Test
    fun doesNotOverwriteExistingFileByDefault() = runBlocking {
        val filesDir = tempFilesDir()
        filesDir.mkdirs()
        val target = File(filesDir, "report.txt")
        target.writeText("old")
        val provider = LocalFileAiToolProvider(filesDir)

        val result = provider.callTool(
            AiToolInvocation(
                name = "write_local_file",
                arguments = arguments(
                    relativePath = "report.txt",
                    content = "new",
                ),
            ),
        )

        assertTrue(result.isError)
        assertEquals("old", target.readText())
    }

    @Test
    fun overwritesExistingFileWhenAllowed() = runBlocking {
        val filesDir = tempFilesDir()
        filesDir.mkdirs()
        val target = File(filesDir, "report.txt")
        target.writeText("old")
        val provider = LocalFileAiToolProvider(filesDir)

        val result = provider.callTool(
            AiToolInvocation(
                name = "write_local_file",
                arguments = arguments(
                    relativePath = "report.txt",
                    content = "new",
                    overwrite = true,
                ),
            ),
        )

        assertFalse(result.isError)
        assertEquals("new", target.readText())
    }

    private fun arguments(
        relativePath: String,
        content: String,
        overwrite: Boolean? = null,
    ): JsonObject {
        val values = mutableMapOf<String, JsonElement>(
            "relative_path" to JsonPrimitive(relativePath),
            "content" to JsonPrimitive(content),
        )
        overwrite?.let {
            values["overwrite"] = JsonPrimitive(it)
        }
        return JsonObject(values)
    }

    private fun tempFilesDir(): File =
        File(
            Files.createTempDirectory("local-file-tool")
                .toFile(),
            "files",
        )
}
