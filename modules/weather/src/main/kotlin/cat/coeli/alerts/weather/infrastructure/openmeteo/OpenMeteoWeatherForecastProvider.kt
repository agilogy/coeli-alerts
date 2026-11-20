package cat.coeli.alerts.weather.infrastructure.openmeteo

import cat.coeli.alerts.weather.domain.DailyForecast
import cat.coeli.alerts.weather.domain.WeatherForecast
import cat.coeli.alerts.weather.domain.WeatherForecastProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import java.time.LocalDate
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

class OpenMeteoWeatherForecastProvider(
    private val httpClient: HttpClient,
    private val geocodingUrl: String = "https://geocoding-api.open-meteo.com/v1/search",
    private val forecastUrl: String = "https://api.open-meteo.com/v1/forecast",
) : WeatherForecastProvider {

    override suspend fun forecast(city: String, days: Int): WeatherForecast? {
        logger.atDebug {
            message = "Requesting weather forecast"
            payload = mapOf("city" to city, "days" to days)
        }
        return findCity(city)?.let { location ->
            WeatherForecast(city = location.name, days = dailyForecast(location, days))
        }
    }

    private suspend fun findCity(city: String): GeocodingResult? =
        httpClient
            .get(geocodingUrl) {
                parameter("name", city)
                parameter("count", 1)
            }
            .body<GeocodingResponse>()
            .results
            .firstOrNull()

    private suspend fun dailyForecast(location: GeocodingResult, days: Int): List<DailyForecast> {
        val daily =
            httpClient
                .get(forecastUrl) {
                    parameter("latitude", location.latitude)
                    parameter("longitude", location.longitude)
                    parameter("daily", "temperature_2m_min,temperature_2m_max,precipitation_sum")
                    parameter("timezone", "auto")
                    parameter("forecast_days", days)
                }
                .body<ForecastResponse>()
                .daily
        return daily.time.indices.map { day ->
            DailyForecast(
                date = LocalDate.parse(daily.time[day]),
                minTemperatureCelsius = daily.minTemperatures[day],
                maxTemperatureCelsius = daily.maxTemperatures[day],
                precipitationMm = daily.precipitationSums[day],
            )
        }
    }
}

private const val CONNECT_TIMEOUT_MILLIS = 5_000L
private const val REQUEST_TIMEOUT_MILLIS = 10_000L
private const val MAX_RETRIES = 2

fun openMeteoHttpClient(engine: HttpClientEngine = CIO.create()): HttpClient =
    HttpClient(engine) {
        expectSuccess = true
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
            requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
        }
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = MAX_RETRIES)
            exponentialDelay()
        }
    }
