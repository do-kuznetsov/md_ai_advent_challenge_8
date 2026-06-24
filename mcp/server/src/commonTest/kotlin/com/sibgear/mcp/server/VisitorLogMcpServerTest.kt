package com.sibgear.mcp.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.sse.SSE
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ResourceUpdatedNotification
import io.modelcontextprotocol.kotlin.sdk.types.SubscribeRequest
import io.modelcontextprotocol.kotlin.sdk.types.SubscribeRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class VisitorLogMcpServerTest {

    @Test
    fun listsAndCallsVisitorLogTool() = runBlocking {
        withTestMcpClient { client, logs, _, _ ->
            val tools = client.listTools().tools
            assertEquals(listOf("visitor_log", "schedule_visit_report"), tools.map { it.name })

            val result = client.callTool(
                name = "visitor_log",
                arguments = mapOf(
                    "userName" to "Dmitry",
                    "localTime" to "2026-06-24 20:00",
                    "city" to "Novosibirsk",
                    "limit" to 1,
                ),
            )
            val textContent = assertIs<TextContent>(result.content.single())
            assertEquals(
                listOf(
                    "Dmitry из Novosibirsk заходил в 2026-06-24 20:00 через visitor-log-test-client/1.0.0",
                    "Погода в Novosibirsk: 18.4 °C",
                ),
                textContent.text.lines(),
            )

            assertTrue(
                logs.any {
                    it.contains("MCP client connection request:") &&
                        it.contains("timestamp=2026-06-22T00:00:00Z") &&
                        it.contains("method=POST") &&
                        it.contains("path=/mcp")
                },
            )
            assertTrue(logs.any { it.startsWith("MCP client connected: activeSessions=") })
        }
    }

    @Test
    fun returnsLatestVisitsNewestFirst() = runBlocking {
        withTestMcpClient { client, _, _, _ ->
            client.callTool(
                name = "visitor_log",
                arguments = mapOf(
                    "userName" to "Anna",
                    "localTime" to "2026-06-24 19:00",
                    "city" to "Tomsk",
                    "limit" to 1,
                ),
            )
            val result = client.callTool(
                name = "visitor_log",
                arguments = mapOf(
                    "userName" to "Boris",
                    "localTime" to "2026-06-24 20:30",
                    "city" to "Omsk",
                    "limit" to 2,
                ),
            )
            val textContent = assertIs<TextContent>(result.content.single())

            assertEquals(
                listOf(
                    "Boris из Omsk заходил в 2026-06-24 20:30 через visitor-log-test-client/1.0.0",
                    "Anna из Tomsk заходил в 2026-06-24 19:00 через visitor-log-test-client/1.0.0",
                    "Погода в Omsk: 18.4 °C",
                ),
                textContent.text.lines(),
            )
        }
    }

    @Test
    fun limitZeroStoresVisitAndReturnsEmptyText() = runBlocking {
        withTestMcpClient { client, _, _, _ ->
            val emptyResult = client.callTool(
                name = "visitor_log",
                arguments = mapOf(
                    "userName" to "Elena",
                    "localTime" to "2026-06-24 21:00",
                    "city" to "Barnaul",
                    "limit" to 0,
                ),
            )
            val emptyTextContent = assertIs<TextContent>(emptyResult.content.single())
            assertEquals("Погода в Barnaul: 18.4 °C", emptyTextContent.text)

            val latestResult = client.callTool(
                name = "visitor_log",
                arguments = mapOf(
                    "userName" to "Fedor",
                    "localTime" to "2026-06-24 21:05",
                    "city" to "Kemerovo",
                    "limit" to 2,
                ),
            )
            val latestTextContent = assertIs<TextContent>(latestResult.content.single())
            assertEquals(
                listOf(
                    "Fedor из Kemerovo заходил в 2026-06-24 21:05 через visitor-log-test-client/1.0.0",
                    "Elena из Barnaul заходил в 2026-06-24 21:00 через visitor-log-test-client/1.0.0",
                    "Погода в Kemerovo: 18.4 °C",
                ),
                latestTextContent.text.lines(),
            )
        }
    }

    @Test
    fun returnsUnavailableWeatherWhenWeatherClientFails() = runBlocking {
        withTestMcpClient(weatherResult = WeatherResult.Unavailable) { client, _, _, _ ->
            val result = client.callTool(
                name = "visitor_log",
                arguments = mapOf(
                    "userName" to "Maria",
                    "localTime" to "2026-06-24 22:00",
                    "city" to "Unknown City",
                    "limit" to 1,
                ),
            )
            val textContent = assertIs<TextContent>(result.content.single())
            assertEquals(
                listOf(
                    "Maria из Unknown City заходил в 2026-06-24 22:00 через visitor-log-test-client/1.0.0",
                    "Погода в Unknown City: недоступна",
                ),
                textContent.text.lines(),
            )
        }
    }

    @Test
    fun schedulesVisitReportAndReturnsPendingResource() = runBlocking {
        withTestMcpClient { client, _, _, _ ->
            val result = client.callTool(
                name = "schedule_visit_report",
                arguments = mapOf("minutes" to 1),
            )
            val text = assertIs<TextContent>(result.content.single()).text
            assertTrue(text.contains("Отчет запланирован."))
            val resourceUri = text.lineWithPrefix("resourceUri:")

            val resourceText = client.readTextResource(resourceUri)
            assertEquals("Отчет ${resourceUri.substringAfterLast('/')} еще готовится.", resourceText)
        }
    }

    @Test
    fun completedVisitReportContainsVisitsFromScheduledInterval() = runBlocking {
        withTestMcpClient { client, _, scheduler, _ ->
            client.callTool(
                name = "visitor_log",
                arguments = mapOf(
                    "userName" to "Before",
                    "localTime" to "2026-06-24 19:50",
                    "city" to "Tomsk",
                    "limit" to 0,
                ),
            )

            val scheduleResult = client.callTool(
                name = "schedule_visit_report",
                arguments = mapOf("minutes" to 1),
            )
            val scheduleText = assertIs<TextContent>(scheduleResult.content.single()).text
            val reportId = scheduleText.lineWithPrefix("reportId:").toLong()
            val resourceUri = scheduleText.lineWithPrefix("resourceUri:")

            client.callTool(
                name = "visitor_log",
                arguments = mapOf(
                    "userName" to "During",
                    "localTime" to "2026-06-24 20:00",
                    "city" to "Novosibirsk",
                    "limit" to 0,
                ),
            )

            scheduler.runReportNow(reportId)

            assertEquals(
                listOf(
                    "За прошедшие 1 минут подключались:",
                    "During из Novosibirsk заходил в 2026-06-24 20:00 через visitor-log-test-client/1.0.0",
                ),
                client.readTextResource(resourceUri).lines(),
            )
        }
    }

    @Test
    fun completedVisitReportShowsEmptyMessageWhenNoVisitsHappened() = runBlocking {
        withTestMcpClient { client, _, scheduler, _ ->
            val scheduleResult = client.callTool(
                name = "schedule_visit_report",
                arguments = mapOf("minutes" to 1),
            )
            val scheduleText = assertIs<TextContent>(scheduleResult.content.single()).text
            val reportId = scheduleText.lineWithPrefix("reportId:").toLong()
            val resourceUri = scheduleText.lineWithPrefix("resourceUri:")

            scheduler.runReportNow(reportId)

            assertEquals(
                listOf(
                    "За прошедшие 1 минут подключались:",
                    "Нет посещений.",
                ),
                client.readTextResource(resourceUri).lines(),
            )
        }
    }

    @Test
    fun sendsResourceUpdatedNotificationWhenReportIsReady() = runBlocking {
        withTestMcpClient(delayProvider = {}) { client, _, _, _ ->
            val updatedResource = CompletableDeferred<String>()
            client.setNotificationHandler<ResourceUpdatedNotification>(
                Method.Defined.NotificationsResourcesUpdated,
            ) { notification ->
                updatedResource.complete(notification.params.uri)
                CompletableDeferred(Unit).apply { complete(Unit) }
            }

            val scheduleResult = client.callTool(
                name = "schedule_visit_report",
                arguments = mapOf("minutes" to 1),
            )
            val scheduleText = assertIs<TextContent>(scheduleResult.content.single()).text
            val resourceUri = scheduleText.lineWithPrefix("resourceUri:")
            client.subscribeResource(SubscribeRequest(SubscribeRequestParams(resourceUri)))

            assertEquals(resourceUri, withTimeout(5_000) { updatedResource.await() })
            assertEquals(
                listOf(
                    "За прошедшие 1 минут подключались:",
                    "Нет посещений.",
                ),
                client.readTextResource(resourceUri).lines(),
            )
        }
    }

    @Test
    fun deletedReportResourceReturnsNotFound() = runBlocking {
        withTestMcpClient { client, _, _, reportRepository ->
            val report = reportRepository.createPendingReport(
                requestedAt = "2026-06-24T00:00:00Z",
                dueAt = "2026-06-24T00:01:00Z",
                minutes = 1,
            )
            reportRepository.completeReport(
                id = report.id,
                reportText = "expired",
                updatedAt = "2026-06-24T00:01:00Z",
            )
            reportRepository.deleteExpiredFinishedReports("2026-06-24T01:01:01Z")

            val error = runCatching {
                client.readTextResource(report.resourceUri)
            }.exceptionOrNull()

            assertEquals("resource ${report.id} not found", error?.message)
        }
    }

    @Test
    fun completionCleansExpiredFinishedReports() = runBlocking {
        withTestMcpClient(delayProvider = {}) { client, _, _, reportRepository ->
            val expiredReport = reportRepository.createPendingReport(
                requestedAt = "2000-01-01T00:00:00Z",
                dueAt = "2000-01-01T00:01:00Z",
                minutes = 1,
            )
            reportRepository.completeReport(
                id = expiredReport.id,
                reportText = "expired",
                updatedAt = "2000-01-01T00:01:00Z",
            )

            client.callTool(
                name = "schedule_visit_report",
                arguments = mapOf("minutes" to 1),
            )

            withTimeout(5_000) {
                while (reportRepository.findReport(expiredReport.id) != null) {
                    delay(10)
                }
            }
        }
    }

    @Test
    fun schedulerRestoresPendingReportsOnStart() = runBlocking {
        val visitRepository = JdbcVisitorLogRepository.inMemory()
        val reportRepository = JdbcVisitReportRepository.inMemory()
        val pendingReport = reportRepository.createPendingReport(
            requestedAt = "2000-01-01T00:00:00Z",
            dueAt = "2000-01-01T00:01:00Z",
            minutes = 1,
        )
        val scheduler = VisitReportScheduler(
            visitLogRepository = visitRepository,
            reportRepository = reportRepository,
            notifyResourceUpdated = { _, _ -> },
            delayProvider = {},
        )

        try {
            scheduler.start()
            val restoredReport = withTimeout(5_000) {
                var report = reportRepository.findReport(pendingReport.id)
                while (report?.status != VisitReportStatus.Completed) {
                    delay(10)
                    report = reportRepository.findReport(pendingReport.id)
                }
                report
            }
            assertEquals(VisitReportStatus.Completed, restoredReport?.status)
            assertEquals(
                listOf(
                    "За прошедшие 1 минут подключались:",
                    "Нет посещений.",
                ),
                restoredReport?.reportText?.lines(),
            )
        } finally {
            scheduler.close()
            visitRepository.close()
            reportRepository.close()
        }
    }

    private suspend fun withTestMcpClient(
        weatherResult: WeatherResult = WeatherResult.Available(18.4),
        delayProvider: suspend (kotlin.time.Duration) -> Unit = { awaitCancellation() },
        block: suspend (Client, List<String>, VisitReportScheduler, VisitReportRepository) -> Unit,
    ) {
        val port = 31337
        val logs = mutableListOf<String>()
        val repository = JdbcVisitorLogRepository.inMemory()
        val reportRepository = JdbcVisitReportRepository.inMemory()
        var scheduler: VisitReportScheduler? = null
        val engine = embeddedServer(ServerCIO, host = "127.0.0.1", port = port) {
            mcpStreamableHttp(path = "/mcp") {
                logMcpConnectionRequest(
                    context = this,
                    log = logs::add,
                    timestampProvider = { "2026-06-22T00:00:00Z" },
                )
                createVisitorLogServer(
                    visitorLogRepository = repository,
                    visitReportRepository = reportRepository,
                    weatherClient = FakeWeatherClient(weatherResult),
                    visitReportSchedulerFactory = { notify ->
                        VisitReportScheduler(
                            visitLogRepository = repository,
                            reportRepository = reportRepository,
                            notifyResourceUpdated = notify,
                            delayProvider = delayProvider,
                        ).also {
                            scheduler = it
                        }
                    },
                    log = logs::add,
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
                        name = "visitor-log-test-client",
                        version = "1.0.0",
                    ),
                )
                val transport = StreamableHttpClientTransport(
                    client = httpClient,
                    url = url,
                )

                client.connect(transport)
                block(client, logs, checkNotNull(scheduler), reportRepository)
            }
        } finally {
            engine.stop()
            scheduler?.close()
            repository.close()
            reportRepository.close()
        }
    }

    private class FakeWeatherClient(
        private val result: WeatherResult,
    ) : WeatherClient {
        override suspend fun getTemperature(city: String): WeatherResult = result
    }
}

private fun String.lineWithPrefix(prefix: String): String =
    lineSequence()
        .first { it.startsWith(prefix) }
        .substringAfter(prefix)
        .trim()

private suspend fun Client.readTextResource(resourceUri: String): String =
    readResource(
        ReadResourceRequest(ReadResourceRequestParams(resourceUri)),
    ).contents.joinToString("\n") { content ->
        when (content) {
            is TextResourceContents -> content.text
            else -> content.toString()
        }
    }
