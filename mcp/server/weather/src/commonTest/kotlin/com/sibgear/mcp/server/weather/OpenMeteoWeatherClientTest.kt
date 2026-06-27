package com.sibgear.mcp.server.weather

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class OpenMeteoWeatherClientTest {
    @Test
    fun russianCityUsesRussianGeocodingFirst() = runBlocking {
        val requestedLanguages = mutableListOf<String?>()
        val client = OpenMeteoWeatherClient(
            httpClient = testHttpClient { url ->
                when (url.encodedPath) {
                    "/v1/search" -> {
                        requestedLanguages += url.parameters["language"]
                        """{"results":[{"latitude":55.03,"longitude":82.92}]}"""
                    }
                    "/v1/forecast" -> """{"current":{"temperature_2m":18.4}}"""
                    else -> error("Unexpected path: ${url.encodedPath}")
                }
            },
        )

        val result = client.getTemperature("Новосибирск")

        assertEquals(WeatherResult.Available(18.4), result)
        assertEquals(listOf<String?>("ru"), requestedLanguages)
    }

    @Test
    fun russianCityFallsBackToEnglishGeocoding() = runBlocking {
        val requestedLanguages = mutableListOf<String?>()
        val client = OpenMeteoWeatherClient(
            httpClient = testHttpClient { url ->
                when (url.encodedPath) {
                    "/v1/search" -> {
                        requestedLanguages += url.parameters["language"]
                        if (url.parameters["language"] == "ru") {
                            """{"results":[]}"""
                        } else {
                            """{"results":[{"latitude":55.03,"longitude":82.92}]}"""
                        }
                    }
                    "/v1/forecast" -> """{"current":{"temperature_2m":19.1}}"""
                    else -> error("Unexpected path: ${url.encodedPath}")
                }
            },
        )

        val result = client.getTemperature("Новосибирск")

        assertEquals(WeatherResult.Available(19.1), result)
        assertEquals(listOf<String?>("ru", "en"), requestedLanguages)
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
