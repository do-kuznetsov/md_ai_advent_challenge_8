package com.sibgear.mcp.server

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val MaxReportMinutes = 1440
private const val MinReportMinutes = 1
private const val ScheduleVisitReportToolName = "schedule_visit_report"
private const val VisitReportUriTemplate = "visitor-report://{reportId}"

internal fun Server.registerScheduleVisitReportTool(
    scheduler: VisitReportScheduler,
) {
    addTool(
        name = ScheduleVisitReportToolName,
        description = "Schedules a delayed visit report and notifies when it is ready.",
        inputSchema = scheduleVisitReportToolSchema(),
    ) { request ->
        val arguments = request.arguments
            ?: throw IllegalArgumentException("Missing schedule_visit_report arguments")
        val minutes = arguments.requiredInt("minutes")
        require(minutes in MinReportMinutes..MaxReportMinutes) {
            "'minutes' must be between $MinReportMinutes and $MaxReportMinutes"
        }

        val report = scheduler.schedule(
            minutes = minutes,
            sessionId = sessionId,
        )

        CallToolResult(
            content = listOf(
                TextContent(
                    text = """
                    Отчет запланирован.
                    reportId: ${report.id}
                    resourceUri: ${report.resourceUri}
                    будет готов примерно: ${report.dueAt}
                    """.trimIndent(),
                ),
            ),
        )
    }
}

internal fun Server.registerVisitReportResource(
    reportRepository: VisitReportRepository,
) {
    addResourceTemplate(
        uriTemplate = VisitReportUriTemplate,
        name = "scheduled_visit_report",
        description = "Scheduled visitor report by id.",
        mimeType = "text/plain",
    ) { request, variables ->
        val reportId = variables["reportId"]?.toLongOrNull()
            ?: throwResourceNotFound(variables["reportId"].orEmpty())
        val report = reportRepository.findReport(reportId)
            ?: throwResourceNotFound(reportId.toString())

        val text = when (report.status) {
            VisitReportStatus.Pending -> "Отчет ${report.id} еще готовится."
            VisitReportStatus.Completed -> report.reportText.orEmpty()
            VisitReportStatus.Failed -> "Отчет ${report.id} завершился с ошибкой."
        }

        ReadResourceResult(
            contents = listOf(
                TextResourceContents(
                    uri = request.uri,
                    mimeType = "text/plain",
                    text = text,
                ),
            ),
        )
    }
}

private fun scheduleVisitReportToolSchema(): ToolSchema =
    ToolSchema(
        properties = buildJsonObject {
            put(
                "minutes",
                buildJsonObject {
                    put("type", "integer")
                    put("description", "How many minutes to wait before preparing the visit report.")
                    put("minimum", MinReportMinutes)
                    put("maximum", MaxReportMinutes)
                },
            )
        },
        required = listOf("minutes"),
    )

private fun JsonObject.requiredInt(key: String): Int =
    requiredPrimitive(key).intOrNull
        ?: throw IllegalArgumentException("Missing or invalid integer '$key'")

private fun JsonObject.requiredPrimitive(key: String): JsonPrimitive =
    get(key)?.jsonPrimitive
        ?: throw IllegalArgumentException("Missing '$key'")

private fun throwResourceNotFound(reportId: String): Nothing {
    throw McpException(
        code = RPCError.ErrorCode.RESOURCE_NOT_FOUND,
        message = "resource $reportId not found",
    )
}
