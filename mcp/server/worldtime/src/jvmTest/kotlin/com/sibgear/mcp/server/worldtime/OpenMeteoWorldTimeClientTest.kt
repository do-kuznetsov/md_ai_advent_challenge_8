package com.sibgear.mcp.server.worldtime

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class OpenMeteoWorldTimeClientTest {
    @Test
    fun russianCityUsesRussianGeocodingFirst() = runBlocking {
        val requestedLanguages = mutableListOf<String?>()
        val client = OpenMeteoWorldTimeClient(
            httpClient = testHttpClient { url ->
                requestedLanguages += url.parameters["language"]
                """{"results":[{"timezone":"Asia/Novosibirsk"}]}"""
            },
            nowProvider = { FixedInstant },
        )

        val result = client.getCurrentTime("Новосибирск")

        assertEquals(
            WorldTimeResult.Available(
                localDateTime = "2026-06-27 19:00:00",
                timezone = "Asia/Novosibirsk",
            ),
            result,
        )
        assertEquals(listOf<String?>("ru"), requestedLanguages)
    }

    @Test
    fun russianCityFallsBackToEnglishGeocoding() = runBlocking {
        val requestedLanguages = mutableListOf<String?>()
        val client = OpenMeteoWorldTimeClient(
            httpClient = testHttpClient { url ->
                requestedLanguages += url.parameters["language"]
                if (url.parameters["language"] == "ru") {
                    """{"results":[]}"""
                } else {
                    """{"results":[{"timezone":"Asia/Novosibirsk"}]}"""
                }
            },
            nowProvider = { FixedInstant },
        )

        val result = client.getCurrentTime("Новосибирск")

        assertEquals(
            WorldTimeResult.Available(
                localDateTime = "2026-06-27 19:00:00",
                timezone = "Asia/Novosibirsk",
            ),
            result,
        )
        assertEquals(listOf<String?>("ru", "en"), requestedLanguages)
    }

    @Test
    fun englishCityFallsBackToRussianGeocoding() = runBlocking {
        val requestedLanguages = mutableListOf<String?>()
        val client = OpenMeteoWorldTimeClient(
            httpClient = testHttpClient { url ->
                requestedLanguages += url.parameters["language"]
                if (url.parameters["language"] == "en") {
                    """{"results":[]}"""
                } else {
                    """{"results":[{"timezone":"Europe/Moscow"}]}"""
                }
            },
            nowProvider = { FixedInstant },
        )

        val result = client.getCurrentTime("Moscow")

        assertEquals(
            WorldTimeResult.Available(
                localDateTime = "2026-06-27 15:00:00",
                timezone = "Europe/Moscow",
            ),
            result,
        )
        assertEquals(listOf<String?>("en", "ru"), requestedLanguages)
    }

    @Test
    fun returnsUnavailableWhenCityIsBlank() = runBlocking {
        val client = OpenMeteoWorldTimeClient(
            httpClient = testHttpClient { error("Geocoding should not be requested") },
            nowProvider = { FixedInstant },
        )

        val result = client.getCurrentTime(" ")

        assertEquals(WorldTimeResult.Unavailable, result)
    }

    @Test
    fun returnsUnavailableWhenGeocodingReturnsNoTimezone() = runBlocking {
        val client = OpenMeteoWorldTimeClient(
            httpClient = testHttpClient { """{"results":[{}]}""" },
            nowProvider = { FixedInstant },
        )

        val result = client.getCurrentTime("Unknown City")

        assertEquals(WorldTimeResult.Unavailable, result)
    }

    @Test
    fun returnsUnavailableWhenTimezoneIsInvalid() = runBlocking {
        val client = OpenMeteoWorldTimeClient(
            httpClient = testHttpClient { """{"results":[{"timezone":"Invalid/Timezone"}]}""" },
            nowProvider = { FixedInstant },
        )

        val result = client.getCurrentTime("Unknown City")

        assertEquals(WorldTimeResult.Unavailable, result)
    }

    private companion object {
        val FixedInstant: Instant = Instant.parse("2026-06-27T12:00:00Z")
    }
}

private fun testHttpClient(responseBody: (io.ktor.http.Url) -> String): HttpClient =
    HttpClient(
        MockEngine { request ->
            respond(
                content = responseBody(request.url),
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        },
    ) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                },
            )
        }
    }
