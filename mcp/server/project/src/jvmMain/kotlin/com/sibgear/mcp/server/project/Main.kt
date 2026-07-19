package com.sibgear.mcp.server.project

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import java.io.File
import kotlin.system.exitProcess

private const val DefaultHost = "127.0.0.1"
private const val DefaultPort = 3003
private const val McpPath = "/mcp"

fun main(args: Array<String>) {
    val log: (String) -> Unit = ::println
    val options = ProjectServerOptions.parse(args).getOrElse { error ->
        log("Ошибка: ${error.message}")
        printUsage(log)
        exitProcess(1)
    }
    val projectDirectory = options.projectDirectory.canonicalFile
    val server = createProjectServer(
        gitRepository = CliProjectGitRepository(projectDirectory),
        log = log,
    )

    log("Starting Project MCP server at http://$DefaultHost:${options.port}$McpPath")
    log("Project directory: ${projectDirectory.absolutePath}")

    embeddedServer(CIO, host = DefaultHost, port = options.port) {
        mcpStreamableHttp(path = McpPath) {
            logMcpConnectionRequest(this, log)
            server
        }
    }.start(wait = true)
}

internal fun createProjectServer(
    gitRepository: ProjectGitRepository,
    log: (String) -> Unit = ::println,
): Server {
    val connectionLogger = McpConnectionLogger(log)
    return Server(
        serverInfo = Implementation(
            name = "project-server",
            version = "1.0.0",
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = false),
            ),
        ),
    ).apply {
        onConnect {
            connectionLogger.onConnect()
        }
        onClose {
            connectionLogger.onClose()
        }
        registerProjectTools(gitRepository)
    }
}

private data class ProjectServerOptions(
    val port: Int,
    val projectDirectory: File,
) {
    companion object {
        fun parse(args: Array<String>): Result<ProjectServerOptions> =
            runCatching {
                val values = args.toList().toOptionMap()
                val project = values["--project"] ?: error("не передан --project.")
                val port = values["--port"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: DefaultPort
                val directory = File(project)
                require(directory.isDirectory) {
                    "--project должен указывать на существующую директорию: ${directory.absolutePath}"
                }
                ProjectServerOptions(
                    port = port,
                    projectDirectory = directory,
                )
            }

        private fun List<String>.toOptionMap(): Map<String, String> {
            val result = mutableMapOf<String, String>()
            var index = 0
            while (index < size) {
                val key = this[index]
                if (!key.startsWith("--")) {
                    error("ожидался аргумент вида --name, получено '$key'.")
                }
                val value = getOrNull(index + 1) ?: error("для $key не передано значение.")
                if (value.startsWith("--")) {
                    error("для $key не передано значение.")
                }
                result[key] = value
                index += 2
            }
            return result
        }
    }
}

private fun printUsage(log: (String) -> Unit) {
    log(
        "Использование: ./gradlew :mcp:server:project:jvmRun " +
            "--args=\"--port 3003 --project /absolute/project/path\"",
    )
}
