package cat.coeli.alerts.weather.domain

import java.time.LocalDate

@ConsistentCopyVisibility
data class WeatherForecast internal constructor(val city: String, val days: List<DailyForecast>)

@ConsistentCopyVisibility
data class DailyForecast
internal constructor(
    val date: LocalDate,
    val minTemperatureCelsius: Double,
    private val maxTemperatureCelsius: Double,
    val precipitationMm: Double,
)
