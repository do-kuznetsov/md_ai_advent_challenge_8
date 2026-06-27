package com.sibgear.mcp.server.visitors

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val ReportRetention: Duration = 1.hours

@OptIn(ExperimentalTime::class)
internal class VisitReportScheduler(
    private val visitLogRepository: VisitorLogRepository,
    private val reportRepository: VisitReportRepository,
    private val notifyResourceUpdated: suspend (sessionId: String, resourceUri: String) -> Unit,
    private val clock: Clock = Clock.System,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val delayProvider: suspend (Duration) -> Unit = { delay(it) },
) : AutoCloseable {
    private val jobs = mutableMapOf<Long, Job>()

    fun start() {
        cleanupExpiredReports()
        reportRepository.findPendingReports().forEach { report ->
            scheduleExistingReport(report, sessionId = null)
        }
    }

    fun schedule(minutes: Int, sessionId: String): VisitReportTask {
        val requestedAt = clock.now()
        val dueAt = requestedAt + minutes.minutes
        val report = reportRepository.createPendingReport(
            requestedAt = requestedAt.toString(),
            dueAt = dueAt.toString(),
            minutes = minutes,
        )
        scheduleExistingReport(report, sessionId)
        return report
    }

    fun cleanupExpiredReports() {
        val expirationBoundary = clock.now() - ReportRetention
        reportRepository.deleteExpiredFinishedReports(expirationBoundary.toString())
    }

    suspend fun runReportNow(reportId: Long) {
        val report = reportRepository.findReport(reportId) ?: return
        completeReport(report, sessionId = null)
    }

    override fun close() {
        scope.cancel()
    }

    private fun scheduleExistingReport(report: VisitReportTask, sessionId: String?) {
        if (report.status != VisitReportStatus.Pending) return

        jobs[report.id]?.cancel()
        jobs[report.id] = scope.launch {
            val dueAt = Instant.parse(report.dueAt)
            val remaining = dueAt - clock.now()
            if (remaining.isPositive()) {
                delayProvider(remaining)
            }
            completeReport(report, sessionId)
        }
    }

    private suspend fun completeReport(report: VisitReportTask, sessionId: String?) {
        runCatching {
            val dueAt = clock.now().toString()
            val visits = visitLogRepository.findBetween(
                createdAtFromInclusive = report.requestedAt,
                createdAtToInclusive = dueAt,
            )
            val reportText = formatReport(report.minutes, visits)
            val completedReport = reportRepository.completeReport(
                id = report.id,
                reportText = reportText,
                updatedAt = dueAt,
            )
            cleanupExpiredReports()
            if (completedReport != null && sessionId != null) {
                notifyResourceUpdated(sessionId, completedReport.resourceUri)
            }
        }.onFailure {
            reportRepository.failReport(report.id, clock.now().toString())
            cleanupExpiredReports()
        }
    }
}

private fun formatReport(minutes: Int, visits: List<VisitorLogEntry>): String {
    val header = "За прошедшие $minutes минут подключались:"
    if (visits.isEmpty()) {
        return "$header\nНет посещений."
    }

    return buildString {
        appendLine(header)
        visits.forEachIndexed { index, visit ->
            if (index > 0) appendLine()
            append(visit.formatForResponse())
        }
    }
}
