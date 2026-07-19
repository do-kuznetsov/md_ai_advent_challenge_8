package com.sibgear.mcp.server.project

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal interface ProjectGitRepository {
    suspend fun currentBranch(): String
    suspend fun shortStatus(): String
}

internal class CliProjectGitRepository(
    private val projectDirectory: File,
) : ProjectGitRepository {
    override suspend fun currentBranch(): String =
        runGit("symbolic-ref", "--short", "HEAD")
            .ifBlank {
                runGit("rev-parse", "--short", "HEAD")
            }

    override suspend fun shortStatus(): String =
        runGit("status", "--short", "--branch")

    private suspend fun runGit(vararg args: String): String =
        withContext(Dispatchers.IO) {
            require(projectDirectory.isDirectory) {
                "Project directory does not exist: ${projectDirectory.absolutePath}"
            }
            val process = ProcessBuilder(listOf("git", "-C", projectDirectory.absolutePath) + args)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                error("git ${args.joinToString(" ")} failed: ${output.ifBlank { "exit code $exitCode" }}")
            }
            output
        }
}
