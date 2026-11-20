package cat.coeli.alerts.weather.infrastructure.openmeteo

import cat.coeli.alerts.weather.domain.DailyForecast
import cat.coeli.alerts.weather.domain.WeatherForecast
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class OpenMeteoWeatherForecastProviderTest {

    @Test
    fun forecastReturnsTheDailyForecastOfTheCity() = runTest {
        val provider =
            providerRespondingWith(
                geocoding = resource("geocoding-barcelona.json"),
                forecast = resource("forecast-3-days.json"),
            )

        val result = provider.forecast("Barcelona", days = 3)

        assertEquals(
            WeatherForecast(
                city = "Barcelona",
                days =
                    listOf(
                        DailyForecast(
                            date = LocalDate.parse("2026-11-20"),
                            minTemperatureCelsius = 10.9,
                            maxTemperatureCelsius = 17.9,
                            precipitationMm = 0.0,
                        ),
                        DailyForecast(
                            date = LocalDate.parse("2026-11-21"),
                            minTemperatureCelsius = 10.6,
                            maxTemperatureCelsius = 17.9,
                            precipitationMm = 0.0,
                        ),
                        DailyForecast(
                            date = LocalDate.parse("2026-11-22"),
                            minTemperatureCelsius = 12.8,
                            maxTemperatureCelsius = 18.7,
                            precipitationMm = 0.3,
                        ),
                    ),
            ),
            result,
        )
    }

    @Test
    fun forecastReturnsNullWhenTheCityIsNotFound() = runTest {
        val provider =
            providerRespondingWith(geocoding = """{"generationtime_ms":0.1}""", forecast = "")

        val result = provider.forecast("Xyzzyqwerty", days = 3)

        assertNull(result)
    }

    @Test
    fun forecastFailsWhenOpenMeteoRespondsWithAnError() = runTest {
        val provider =
            OpenMeteoWeatherForecastProvider(
                openMeteoHttpClient(MockEngine { respond("", HttpStatusCode.InternalServerError) }),
            )

        assertFails { provider.forecast("Barcelona", days = 3) }
    }

    @Test
    fun forecastRetriesWhenOpenMeteoFailsTemporarily() = runTest {
        var requests = 0
        val provider =
            OpenMeteoWeatherForecastProvider(
                openMeteoHttpClient(
                    MockEngine { request ->
                        requests++
                        when {
                            requests == 1 -> respond("", HttpStatusCode.ServiceUnavailable)
                            request.url.host.startsWith("geocoding") ->
                                respondJson(resource("geocoding-barcelona.json"))
                            else -> respondJson(resource("forecast-3-days.json"))
                        }
                    },
                ),
            )

        val result = provider.forecast("Barcelona", days = 3)

        assertEquals("Barcelona", result?.city)
        assertEquals(3, requests)
    }

    private fun providerRespondingWith(geocoding: String, forecast: String) =
        OpenMeteoWeatherForecastProvider(
            openMeteoHttpClient(
                MockEngine { request ->
                    respondJson(
                        if (request.url.host.startsWith("geocoding")) geocoding else forecast,
                    )
                },
            ),
        )

    private fun MockRequestHandleScope.respondJson(content: String) =
        respond(content, headers = headersOf(HttpHeaders.ContentType, "application/json"))

    private fun resource(name: String): String =
        requireNotNull(javaClass.getResource("/openmeteo/$name")) { "Missing test resource $name" }
            .readText()
}
