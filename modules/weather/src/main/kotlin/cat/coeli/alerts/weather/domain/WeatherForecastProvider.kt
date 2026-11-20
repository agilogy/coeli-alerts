package cat.coeli.alerts.weather.domain

interface WeatherForecastProvider {
    /** Starts today; null if the city isn't found. */
    suspend fun forecast(city: String, days: Int): WeatherForecast?
}
