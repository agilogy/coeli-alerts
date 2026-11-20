package cat.coeli.alerts.weather.domain

interface WeatherForecastProvider {
    /** Daily forecast for [city], starting today, or null if no city with that name is found. */
    suspend fun forecast(city: String, days: Int): WeatherForecast?
}
