package cat.coeli.alerts.weather.domain

import java.time.LocalDate

fun weatherForecast(city: String, vararg days: DailyForecast): WeatherForecast =
    WeatherForecast(city, days.toList())

fun dailyForecast(
    date: LocalDate,
    minTemperatureCelsius: Double,
    maxTemperatureCelsius: Double,
    precipitationMm: Double,
): DailyForecast =
    DailyForecast(
        date = date,
        minTemperatureCelsius = minTemperatureCelsius,
        maxTemperatureCelsius = maxTemperatureCelsius,
        precipitationMm = precipitationMm,
    )
