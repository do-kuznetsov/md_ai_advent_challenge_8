package com.sibgear.mcp.server

import io.ktor.http.HttpHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.routing.RoutingContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val McpSessionIdHeader = "Mcp-Session-Id"

internal class McpConnectionLogger(
    private val log: (String) -> Unit,
) {
    private var isConnected = false

    fun onConnect() {
        if (isConnected) return

        isConnected = true
        log("MCP client connected: activeSessions=1")
    }

    fun onClose() {
        if (!isConnected) return

        isConnected = false
        log("MCP client disconnected: activeSessions=0")
    }
}

internal fun logMcpConnectionRequest(
    context: RoutingContext,
    log: (String) -> Unit = ::println,
    timestampProvider: () -> String = ::currentTimestamp,
) {
    val request = context.call.request
    val remoteHost = request.origin.remoteHost
    val userAgent = request.header(HttpHeaders.UserAgent) ?: "unknown"
    val mcpSessionId = request.header(McpSessionIdHeader) ?: "none"

    log(
        "MCP client connection request: " +
            "timestamp=${timestampProvider()} " +
            "remote=$remoteHost " +
            "method=${request.httpMethod.value} " +
            "path=${request.path()} " +
            "userAgent=$userAgent " +
            "mcpSessionId=$mcpSessionId",
    )
}

@OptIn(ExperimentalTime::class)
internal fun currentTimestamp(): String = Clock.System.now().toString()
