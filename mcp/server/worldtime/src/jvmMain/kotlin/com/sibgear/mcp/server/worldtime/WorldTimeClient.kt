package com.sibgear.mcp.server.worldtime

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal interface WorldTimeClient {
    suspend fun getCurrentTime(city: String): WorldTimeResult
}

internal sealed interface WorldTimeResult {
    data class Available(
        val localDateTime: String,
        val timezone: String,
    ) : WorldTimeResult

    data object Unavailable : WorldTimeResult
}

internal class OpenMeteoWorldTimeClient(
    private val httpClient: HttpClient = defaultHttpClient(),
    private val nowProvider: () -> Instant = Instant::now,
) : WorldTimeClient {

    override suspend fun getCurrentTime(city: String): WorldTimeResult =
        runCatching {
            if (city.isBlank()) return WorldTimeResult.Unavailable

            val location = findLocation(city) ?: return WorldTimeResult.Unavailable
            val timezone = location.timezone ?: return WorldTimeResult.Unavailable
            val zoneId = ZoneId.of(timezone)
            val localTime = ZonedDateTime
                .ofInstant(nowProvider(), zoneId)
                .format(OutputFormatter)

            WorldTimeResult.Available(
                localDateTime = localTime,
                timezone = timezone,
            )
        }.getOrDefault(WorldTimeResult.Unavailable)

    private suspend fun findLocation(city: String): GeocodingLocation? {
        val languages = if (city.any { it in CyrillicRange }) {
            listOf("ru", "en")
        } else {
            listOf("en", "ru")
        }

        languages.forEach { language ->
            val response = httpClient.get("https://geocoding-api.open-meteo.com/v1/search") {
                parameter("name", city)
                parameter("count", 1)
                parameter("language", language)
                parameter("format", "json")
            }.body<GeocodingResponse>()

            response.results
                ?.firstOrNull { !it.timezone.isNullOrBlank() }
                ?.let { location ->
                    return location.copy(timezone = location.timezone?.trim())
                }
        }

        return null
    }

    companion object {
        private val OutputFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        private fun defaultHttpClient(): HttpClient =
            HttpClient(CIO) {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                        },
                    )
                }
            }
    }
}

private val CyrillicRange = '\u0400'..'\u04FF'

@Serializable
private data class GeocodingResponse(
    val results: List<GeocodingLocation>? = null,
)

@Serializable
private data class GeocodingLocation(
    val timezone: String? = null,
)
