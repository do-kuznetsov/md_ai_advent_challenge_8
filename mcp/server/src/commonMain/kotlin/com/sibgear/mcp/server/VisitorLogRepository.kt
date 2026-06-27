package com.sibgear.mcp.server

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement

internal data class VisitorLogEntry(
    val id: Long? = null,
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
    fun add(entry: VisitorLogEntry): VisitorLogEntry
    fun count(): Int
    fun findRecent(limit: Int, offset: Int = 0): List<VisitorLogEntry>
    fun findBetween(createdAtFromInclusive: String, createdAtToInclusive: String): List<VisitorLogEntry>
}

internal class JdbcVisitorLogRepository private constructor(
    private val jdbcUrl: String,
) : VisitorLogRepository, AutoCloseable {
    private val connection: Connection = DriverManager.getConnection(jdbcUrl)

    init {
        connection.autoCommit = true
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

    override fun add(entry: VisitorLogEntry): VisitorLogEntry =
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
                Statement.RETURN_GENERATED_KEYS,
            ).use { statement ->
                statement.setString(1, entry.userName)
                statement.setString(2, entry.localTime)
                statement.setString(3, entry.city)
                statement.setString(4, entry.clientName)
                statement.setString(5, entry.clientVersion)
                statement.setString(6, entry.createdAt)
                statement.executeUpdate()
                val id = statement.generatedKeys.use { keys ->
                    if (keys.next()) keys.getLong(1) else null
                }
                entry.copy(id = id)
            }
        }

    override fun count(): Int =
        synchronized(connection) {
            connection.prepareStatement("SELECT COUNT(*) FROM visitor_log").use { statement ->
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) resultSet.getInt(1) else 0
                }
            }
        }

    override fun findRecent(limit: Int, offset: Int): List<VisitorLogEntry> =
        synchronized(connection) {
            if (limit <= 0) {
                emptyList()
            } else {
                connection.prepareStatement(
                    """
                    SELECT id, user_name, local_time, city, client_name, client_version, created_at
                    FROM visitor_log
                    ORDER BY id DESC
                    LIMIT ?
                    OFFSET ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setInt(1, limit)
                    statement.setInt(2, offset.coerceAtLeast(0))
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

    override fun findBetween(
        createdAtFromInclusive: String,
        createdAtToInclusive: String,
    ): List<VisitorLogEntry> =
        synchronized(connection) {
            connection.prepareStatement(
                """
                SELECT id, user_name, local_time, city, client_name, client_version, created_at
                FROM visitor_log
                WHERE created_at >= ? AND created_at <= ?
                ORDER BY id ASC
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, createdAtFromInclusive)
                statement.setString(2, createdAtToInclusive)
                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            add(resultSet.toVisitorLogEntry())
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
            id = getLong("id"),
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
