package com.sibgear.mcp.server

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

internal data class VisitorLogEntry(
    val userName: String,
    val localTime: String,
    val city: String,
    val clientName: String,
    val clientVersion: String,
    val createdAt: String,
) {
    fun formatForResponse(): String =
        "$userName из $city заходил в $localTime через $clientName/$clientVersion"
}

internal interface VisitorLogRepository {
    fun addAndGetRecent(entry: VisitorLogEntry, limit: Int): List<VisitorLogEntry>
}

internal class JdbcVisitorLogRepository private constructor(
    private val jdbcUrl: String,
) : VisitorLogRepository, AutoCloseable {
    private val connection: Connection = DriverManager.getConnection(jdbcUrl)

    init {
        connection.createStatement().use { statement ->
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS visitor_log(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_name TEXT NOT NULL,
                    local_time TEXT NOT NULL,
                    city TEXT NOT NULL,
                    client_name TEXT NOT NULL,
                    client_version TEXT NOT NULL,
                    created_at TEXT NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    override fun addAndGetRecent(entry: VisitorLogEntry, limit: Int): List<VisitorLogEntry> =
        synchronized(connection) {
            connection.prepareStatement(
                """
                INSERT INTO visitor_log(
                    user_name,
                    local_time,
                    city,
                    client_name,
                    client_version,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, entry.userName)
                statement.setString(2, entry.localTime)
                statement.setString(3, entry.city)
                statement.setString(4, entry.clientName)
                statement.setString(5, entry.clientVersion)
                statement.setString(6, entry.createdAt)
                statement.executeUpdate()
            }

            if (limit <= 0) {
                emptyList()
            } else {
                connection.prepareStatement(
                    """
                    SELECT user_name, local_time, city, client_name, client_version, created_at
                    FROM visitor_log
                    ORDER BY id DESC
                    LIMIT ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setInt(1, limit)
                    statement.executeQuery().use { resultSet ->
                        buildList {
                            while (resultSet.next()) {
                                add(resultSet.toVisitorLogEntry())
                            }
                        }
                    }
                }
            }
        }

    override fun close() {
        connection.close()
    }

    private fun ResultSet.toVisitorLogEntry(): VisitorLogEntry =
        VisitorLogEntry(
            userName = getString("user_name"),
            localTime = getString("local_time"),
            city = getString("city"),
            clientName = getString("client_name"),
            clientVersion = getString("client_version"),
            createdAt = getString("created_at"),
        )

    companion object {
        fun file(databaseFile: File): JdbcVisitorLogRepository {
            databaseFile.parentFile?.mkdirs()
            return JdbcVisitorLogRepository("jdbc:sqlite:${databaseFile.absolutePath}")
        }

        fun inMemory(): JdbcVisitorLogRepository =
            JdbcVisitorLogRepository("jdbc:sqlite::memory:")
    }
}
