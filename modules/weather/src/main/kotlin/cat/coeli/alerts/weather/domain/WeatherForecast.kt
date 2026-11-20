package cat.coeli.alerts.weather.domain

import java.time.LocalDate

data class WeatherForecast(val city: String, val days: List<DailyForecast>)

data class DailyForecast(
    val date: LocalDate,
    val minTemperatureCelsius: Double,
    val maxTemperatureCelsius: Double,
    val precipitationMm: Double,
)
