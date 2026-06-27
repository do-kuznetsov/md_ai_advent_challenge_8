package com.sibgear.mcp.server.weather

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal interface WeatherClient {
    suspend fun getTemperature(city: String): WeatherResult
}

internal sealed interface WeatherResult {
    data class Available(val temperatureCelsius: Double) : WeatherResult
    data object Unavailable : WeatherResult
}

internal class OpenMeteoWeatherClient(
    private val httpClient: HttpClient = defaultHttpClient(),
) : WeatherClient {

    override suspend fun getTemperature(city: String): WeatherResult =
        runCatching {
            val location = findLocation(city) ?: return WeatherResult.Unavailable
            val forecast = httpClient.get("https://api.open-meteo.com/v1/forecast") {
                parameter("latitude", location.latitude)
                parameter("longitude", location.longitude)
                parameter("current", "temperature_2m")
                parameter("temperature_unit", "celsius")
            }.body<ForecastResponse>()

            forecast.current?.temperature2m?.let(WeatherResult::Available)
                ?: WeatherResult.Unavailable
        }.getOrDefault(WeatherResult.Unavailable)

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

            response.results?.firstOrNull()?.let { location ->
                return location
            }
        }

        return null
    }

    companion object {
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
    val latitude: Double,
    val longitude: Double,
)

@Serializable
private data class ForecastResponse(
    val current: CurrentWeather? = null,
)

@Serializable
private data class CurrentWeather(
    @SerialName("temperature_2m")
    val temperature2m: Double? = null,
)
