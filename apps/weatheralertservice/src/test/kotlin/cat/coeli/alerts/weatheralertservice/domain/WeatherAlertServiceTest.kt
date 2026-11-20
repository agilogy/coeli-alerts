package cat.coeli.alerts.weatheralertservice.domain

import cat.coeli.alerts.alertsrepository.domain.AlertLevel
import cat.coeli.alerts.alertsrepository.domain.InMemoryAlertRepository
import cat.coeli.alerts.testutils.FixedClock
import cat.coeli.alerts.weather.domain.DailyForecast
import cat.coeli.alerts.weather.domain.FakeWeatherForecastProvider
import cat.coeli.alerts.weather.domain.WeatherForecast
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class WeatherAlertServiceTest {

    private val now = Instant.parse("2026-11-20T08:00:00Z")
    private val alertRepository = InMemoryAlertRepository()

    @Test
    fun runRecordsTheWeatherAlertsAndReturnsTheRecentAlerts() = runTest {
        val service =
            service(
                forecast(
                    day("2026-11-21", minTemperature = 10.6, precipitation = 0.0),
                    day("2026-11-22", minTemperature = 12.8, precipitation = 0.3),
                ),
            )

        val result = service.run("Barcelona")

        assertEquals(
            listOf("Barcelona 2026-11-22: rain 0.3 mm, min 12.8 °C"),
            alertRepository.alerts.map { it.message },
        )
        assertEquals(alertRepository.alerts, result)
    }

    @Test
    fun runFailsWhenTheCityIsNotFound() = runTest {
        val service = service(forecast = null)

        assertFails { service.run("Xyzzyqwerty") }

        assertEquals(emptyList(), alertRepository.alerts)
    }

    @Test
    fun alertsOnRainyDays() {
        val forecast = forecast(day("2026-11-22", minTemperature = 12.8, precipitation = 0.3))

        val alerts = service(forecast).weatherAlerts(forecast)

        assertEquals(
            listOf("Barcelona 2026-11-22: rain 0.3 mm, min 12.8 °C"),
            alerts.map { it.message },
        )
        assertEquals(listOf(AlertLevel.WARNING), alerts.map { it.level })
        assertEquals(listOf(now), alerts.map { it.occurredAt })
    }

    @Test
    fun alertsOnDaysWithAMinimumBelow10Degrees() {
        val forecast = forecast(day("2026-11-23", minTemperature = 9.9, precipitation = 0.0))

        val alerts = service(forecast).weatherAlerts(forecast)

        assertEquals(
            listOf("Barcelona 2026-11-23: rain 0.0 mm, min 9.9 °C"),
            alerts.map { it.message },
        )
    }

    @Test
    fun doesNotAlertOnDryDaysWithAMinimumOf10DegreesOrMore() {
        val forecast =
            forecast(
                day("2026-11-20", minTemperature = 10.0, precipitation = 0.0),
                day("2026-11-21", minTemperature = 10.6, precipitation = 0.0),
            )

        val alerts = service(forecast).weatherAlerts(forecast)

        assertEquals(emptyList(), alerts)
    }

    @Test
    fun alertsOncePerMatchingDay() {
        val forecast =
            forecast(
                day("2026-11-20", minTemperature = 10.9, precipitation = 0.0),
                day("2026-11-21", minTemperature = 8.0, precipitation = 2.5),
                day("2026-11-22", minTemperature = 12.8, precipitation = 0.3),
            )

        val alerts = service(forecast).weatherAlerts(forecast)

        assertEquals(
            listOf(
                "Barcelona 2026-11-21: rain 2.5 mm, min 8.0 °C",
                "Barcelona 2026-11-22: rain 0.3 mm, min 12.8 °C",
            ),
            alerts.map { it.message },
        )
    }

    private fun service(forecast: WeatherForecast?) =
        WeatherAlertService(
            forecastProvider = FakeWeatherForecastProvider(forecast),
            alertRepository = alertRepository,
            clock = FixedClock(now),
        )

    private fun forecast(vararg days: DailyForecast) = WeatherForecast("Barcelona", days.toList())

    private fun day(date: String, minTemperature: Double, precipitation: Double) =
        DailyForecast(
            date = LocalDate.parse(date),
            minTemperatureCelsius = minTemperature,
            maxTemperatureCelsius = minTemperature + 5,
            precipitationMm = precipitation,
        )
}
