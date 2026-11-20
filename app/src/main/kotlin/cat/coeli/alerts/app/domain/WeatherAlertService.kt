package cat.coeli.alerts.app.domain

import cat.coeli.alerts.alertsrepository.domain.Alert
import cat.coeli.alerts.alertsrepository.domain.AlertId
import cat.coeli.alerts.alertsrepository.domain.AlertLevel
import cat.coeli.alerts.alertsrepository.domain.AlertRepository
import cat.coeli.alerts.alertsrepository.domain.Pagination
import cat.coeli.alerts.weather.domain.WeatherForecast
import cat.coeli.alerts.weather.domain.WeatherForecastProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.Clock
import kotlin.uuid.Uuid

private val logger = KotlinLogging.logger {}

private const val FORECAST_DAYS = 7
private const val COLD_THRESHOLD_CELSIUS = 10.0
private const val RECENT_ALERTS = 20

/**
 * Turns the weather forecast into alerts.
 *
 * It depends only on domain interfaces, received in the constructor: `Main` decides which
 * implementations to use (Open-Meteo, PostgreSQL, the system clock), and tests pass fakes.
 */
class WeatherAlertService(
    private val forecastProvider: WeatherForecastProvider,
    private val alertRepository: AlertRepository,
    private val clock: Clock,
) {
    /**
     * Records the alerts for the next 7 days of the forecast of [city] and returns the most recent
     * alerts.
     */
    suspend fun run(city: String): List<Alert> {
        val forecast =
            checkNotNull(forecastProvider.forecast(city, FORECAST_DAYS)) { "City $city not found" }
        val alerts = weatherAlerts(forecast)
        alerts.forEach { alertRepository.record(it) }
        logger.atInfo {
            message = "Recorded weather alerts"
            payload = mapOf("city" to city, "days" to FORECAST_DAYS, "alerts" to alerts.size)
        }
        return alertRepository.search(AlertLevel.INFO, Pagination(offset = 0, size = RECENT_ALERTS))
    }

    /**
     * A WARNING alert, occurred now, for each day of the forecast with rain or a minimum below 10
     * °C.
     */
    fun weatherAlerts(forecast: WeatherForecast): List<Alert> =
        forecast.days
            .filter { it.precipitationMm > 0 || it.minTemperatureCelsius < COLD_THRESHOLD_CELSIUS }
            .map { day ->
                Alert(
                    id = AlertId(Uuid.random()),
                    level = AlertLevel.WARNING,
                    message =
                        "${forecast.city} ${day.date}: rain ${day.precipitationMm} mm, " +
                            "min ${day.minTemperatureCelsius} °C",
                    occurredAt = clock.now(),
                )
            }
}
