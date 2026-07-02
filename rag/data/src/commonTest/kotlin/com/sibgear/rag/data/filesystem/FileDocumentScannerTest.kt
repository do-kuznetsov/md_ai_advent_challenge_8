package com.sibgear.rag.data.filesystem

import java.nio.file.Files
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileDocumentScannerTest {
    @Test
    fun scansTextFilesAndSkipsPdfBinaryAndBuildDirs() = runTest {
        val root = Files.createTempDirectory("rag-scanner")
        root.resolve("README.md").writeText("# Hello")
        root.resolve("manual.pdf").writeBytes(byteArrayOf(1, 2, 3))
        root.resolve("image.png").writeBytes(byteArrayOf(0, 1, 2))
        Files.createDirectories(root.resolve("build"))
        root.resolve("build/generated.kt").writeText("generated")

        val result = FileDocumentScanner().scan(root.toString())

        assertEquals(listOf("README.md"), result.documents.map { it.source })
        assertTrue(result.warnings.any { it.startsWith("PDF skipped") })
        assertTrue(result.warnings.any { it.startsWith("Binary skipped") })
    }
}
