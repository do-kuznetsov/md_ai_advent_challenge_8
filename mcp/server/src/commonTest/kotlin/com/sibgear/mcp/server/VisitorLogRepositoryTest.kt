package com.sibgear.mcp.server

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class VisitorLogRepositoryTest {
    @Test
    fun addPersistsEntryInDatabaseFile() {
        val databaseFile = File.createTempFile("visitor-log-test", ".db")
        databaseFile.deleteOnExit()
        val entry = VisitorLogEntry(
            userName = "Dmitry",
            localTime = "2026-06-24 20:00",
            city = "Novosibirsk",
            clientName = "test-client",
            clientVersion = "1.0.0",
            createdAt = "2026-06-24T13:00:00Z",
        )

        val saved = JdbcVisitorLogRepository.file(databaseFile).use { repository ->
            repository.add(entry)
        }
        val restored = JdbcVisitorLogRepository.file(databaseFile).use { repository ->
            repository.findRecent(limit = 1).single()
        }

        assertNotNull(saved.id)
        assertEquals(saved, restored)
    }

    @Test
    fun countAndFindRecentSupportPagination() {
        val databaseFile = File.createTempFile("visitor-log-pagination-test", ".db")
        databaseFile.deleteOnExit()

        JdbcVisitorLogRepository.file(databaseFile).use { repository ->
            listOf("Anna", "Boris", "Clara").forEachIndexed { index, name ->
                repository.add(
                    VisitorLogEntry(
                        userName = name,
                        localTime = "2026-06-24 20:0$index",
                        city = "Tomsk",
                        clientName = "test-client",
                        clientVersion = "1.0.0",
                        createdAt = "2026-06-24T13:0${index}:00Z",
                    ),
                )
            }

            assertEquals(3, repository.count())
            assertEquals(
                listOf("Boris"),
                repository.findRecent(limit = 1, offset = 1).map { it.userName },
            )
        }
    }
}
