package com.sibgear.mcp.server.project

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject

private const val GetGitBranchToolName = "get_git_branch"
private const val GetGitStatusToolName = "get_git_status"

internal fun Server.registerProjectTools(
    gitRepository: ProjectGitRepository,
) {
    addTool(
        name = GetGitBranchToolName,
        description = "Returns the current git branch for the configured project directory.",
        inputSchema = emptyToolSchema(),
    ) {
        CallToolResult(
            content = listOf(TextContent(text = "git branch: ${gitRepository.currentBranch()}")),
        )
    }

    addTool(
        name = GetGitStatusToolName,
        description = "Returns short git status for the configured project directory.",
        inputSchema = emptyToolSchema(),
    ) {
        CallToolResult(
            content = listOf(TextContent(text = gitRepository.shortStatus())),
        )
    }
}

private fun emptyToolSchema(): ToolSchema =
    ToolSchema(
        properties = buildJsonObject {},
    )
