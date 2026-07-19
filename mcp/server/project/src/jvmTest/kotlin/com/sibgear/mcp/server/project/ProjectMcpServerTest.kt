package com.sibgear.mcp.server.project

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.sse.SSE
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProjectMcpServerTest {
    @Test
    fun listsAndCallsProjectTools() = runBlocking {
        val repository = FakeProjectGitRepository(
            branch = "feature/day31",
            status = "## feature/day31\n M README.md",
        )

        withTestMcpClient(repository) { client ->
            val tools = client.listTools().tools
            assertEquals(listOf("get_git_branch", "get_git_status"), tools.map { it.name })

            val branchResult = client.callTool(
                name = "get_git_branch",
                arguments = emptyMap(),
            )
            val branchText = assertIs<TextContent>(branchResult.content.single())
            assertEquals("git branch: feature/day31", branchText.text)

            val statusResult = client.callTool(
                name = "get_git_status",
                arguments = emptyMap(),
            )
            val statusText = assertIs<TextContent>(statusResult.content.single())
            assertEquals("## feature/day31\n M README.md", statusText.text)
        }
    }

    @Test
    fun gitRepositoryReturnsCurrentBranchFromTempRepo() = runBlocking {
        val repo = createTempGitRepository()

        val branch = CliProjectGitRepository(repo).currentBranch()

        assertEquals("feature/day31", branch)
    }

    @Test
    fun invalidProjectDirectoryFailsClearly() = runBlocking {
        val missing = File(Files.createTempDirectory("project-mcp-missing").toFile(), "missing")

        val error = assertFailsWith<IllegalArgumentException> {
            CliProjectGitRepository(missing).currentBranch()
        }

        assertTrue(error.message.orEmpty().contains("Project directory does not exist"))
    }

    private suspend fun withTestMcpClient(
        gitRepository: ProjectGitRepository,
        block: suspend (Client) -> Unit,
    ) {
        val port = 31340
        val engine = embeddedServer(ServerCIO, host = "127.0.0.1", port = port) {
            mcpStreamableHttp(path = "/mcp") {
                createProjectServer(
                    gitRepository = gitRepository,
                    log = {},
                )
            }
        }.start(wait = false)

        try {
            val url = "http://127.0.0.1:$port/mcp"

            HttpClient(ClientCIO) {
                install(SSE)
            }.use { httpClient ->
                val client = Client(
                    clientInfo = Implementation(
                        name = "project-test-client",
                        version = "1.0.0",
                    ),
                )
                val transport = StreamableHttpClientTransport(
                    client = httpClient,
                    url = url,
                )

                client.connect(transport)
                block(client)
            }
        } finally {
            engine.stop()
        }
    }

    private fun createTempGitRepository(): File {
        val directory = Files.createTempDirectory("project-mcp-git").toFile()
        runProcess(directory, "git", "init")
        runProcess(directory, "git", "checkout", "-b", "feature/day31")
        return directory
    }

    private fun runProcess(
        directory: File,
        vararg command: String,
    ) {
        val process = ProcessBuilder(command.toList())
            .directory(directory)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        check(exitCode == 0) {
            "${command.joinToString(" ")} failed: $output"
        }
    }

    private class FakeProjectGitRepository(
        private val branch: String,
        private val status: String,
    ) : ProjectGitRepository {
        override suspend fun currentBranch(): String = branch
        override suspend fun shortStatus(): String = status
    }
}
