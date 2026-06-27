package com.sibgear.mcp.server.visitors

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

private const val CompletedStatus = "completed"
private const val FailedStatus = "failed"
private const val PendingStatus = "pending"

internal enum class VisitReportStatus(
    val storageValue: String,
) {
    Pending(PendingStatus),
    Completed(CompletedStatus),
    Failed(FailedStatus),
}

internal data class VisitReportTask(
    val id: Long,
    val requestedAt: String,
    val dueAt: String,
    val minutes: Int,
    val status: VisitReportStatus,
    val reportText: String?,
    val createdAt: String,
    val updatedAt: String,
) {
    val resourceUri: String = visitReportResourceUri(id)
}

internal interface VisitReportRepository {
    fun createPendingReport(requestedAt: String, dueAt: String, minutes: Int): VisitReportTask
    fun findReport(id: Long): VisitReportTask?
    fun findPendingReports(): List<VisitReportTask>
    fun completeReport(id: Long, reportText: String, updatedAt: String): VisitReportTask?
    fun failReport(id: Long, updatedAt: String): VisitReportTask?
    fun deleteExpiredFinishedReports(expireBeforeUpdatedAt: String)
}

internal class JdbcVisitReportRepository private constructor(
    private val jdbcUrl: String,
) : VisitReportRepository, AutoCloseable {
    private val connection: Connection = DriverManager.getConnection(jdbcUrl)

    init {
        connection.createStatement().use { statement ->
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS scheduled_visit_reports(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    requested_at TEXT NOT NULL,
                    due_at TEXT NOT NULL,
                    minutes INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    report_text TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    override fun createPendingReport(
        requestedAt: String,
        dueAt: String,
        minutes: Int,
    ): VisitReportTask =
        synchronized(connection) {
            connection.prepareStatement(
                """
                INSERT INTO scheduled_visit_reports(
                    requested_at,
                    due_at,
                    minutes,
                    status,
                    report_text,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, NULL, ?, ?)
                """.trimIndent(),
                java.sql.Statement.RETURN_GENERATED_KEYS,
            ).use { statement ->
                statement.setString(1, requestedAt)
                statement.setString(2, dueAt)
                statement.setInt(3, minutes)
                statement.setString(4, VisitReportStatus.Pending.storageValue)
                statement.setString(5, requestedAt)
                statement.setString(6, requestedAt)
                statement.executeUpdate()
                statement.generatedKeys.use { keys ->
                    check(keys.next()) { "Failed to create scheduled visit report" }
                    findReportLocked(keys.getLong(1))
                        ?: error("Created scheduled visit report was not found")
                }
            }
        }

    override fun findReport(id: Long): VisitReportTask? =
        synchronized(connection) {
            findReportLocked(id)
        }

    override fun findPendingReports(): List<VisitReportTask> =
        synchronized(connection) {
            connection.prepareStatement(
                """
                SELECT id, requested_at, due_at, minutes, status, report_text, created_at, updated_at
                FROM scheduled_visit_reports
                WHERE status = ?
                ORDER BY id ASC
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, VisitReportStatus.Pending.storageValue)
                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            add(resultSet.toVisitReportTask())
                        }
                    }
                }
            }
        }

    override fun completeReport(
        id: Long,
        reportText: String,
        updatedAt: String,
    ): VisitReportTask? =
        updateFinishedReport(
            id = id,
            status = VisitReportStatus.Completed,
            reportText = reportText,
            updatedAt = updatedAt,
        )

    override fun failReport(id: Long, updatedAt: String): VisitReportTask? =
        updateFinishedReport(
            id = id,
            status = VisitReportStatus.Failed,
            reportText = null,
            updatedAt = updatedAt,
        )

    override fun deleteExpiredFinishedReports(expireBeforeUpdatedAt: String) {
        synchronized(connection) {
            connection.prepareStatement(
                """
                DELETE FROM scheduled_visit_reports
                WHERE status IN (?, ?) AND updated_at < ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, VisitReportStatus.Completed.storageValue)
                statement.setString(2, VisitReportStatus.Failed.storageValue)
                statement.setString(3, expireBeforeUpdatedAt)
                statement.executeUpdate()
            }
        }
    }

    override fun close() {
        connection.close()
    }

    private fun updateFinishedReport(
        id: Long,
        status: VisitReportStatus,
        reportText: String?,
        updatedAt: String,
    ): VisitReportTask? =
        synchronized(connection) {
            connection.prepareStatement(
                """
                UPDATE scheduled_visit_reports
                SET status = ?, report_text = ?, updated_at = ?
                WHERE id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, status.storageValue)
                statement.setString(2, reportText)
                statement.setString(3, updatedAt)
                statement.setLong(4, id)
                statement.executeUpdate()
            }
            findReportLocked(id)
        }

    private fun findReportLocked(id: Long): VisitReportTask? =
        connection.prepareStatement(
            """
            SELECT id, requested_at, due_at, minutes, status, report_text, created_at, updated_at
            FROM scheduled_visit_reports
            WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setLong(1, id)
            statement.executeQuery().use { resultSet ->
                if (resultSet.next()) {
                    resultSet.toVisitReportTask()
                } else {
                    null
                }
            }
        }

    private fun ResultSet.toVisitReportTask(): VisitReportTask =
        VisitReportTask(
            id = getLong("id"),
            requestedAt = getString("requested_at"),
            dueAt = getString("due_at"),
            minutes = getInt("minutes"),
            status = VisitReportStatus.entries.first { it.storageValue == getString("status") },
            reportText = getString("report_text"),
            createdAt = getString("created_at"),
            updatedAt = getString("updated_at"),
        )

    companion object {
        fun file(databaseFile: File): JdbcVisitReportRepository {
            databaseFile.parentFile?.mkdirs()
            return JdbcVisitReportRepository("jdbc:sqlite:${databaseFile.absolutePath}")
        }

        fun inMemory(): JdbcVisitReportRepository =
            JdbcVisitReportRepository("jdbc:sqlite::memory:")
    }
}

internal fun visitReportResourceUri(reportId: Long): String = "visitor-report://$reportId"
