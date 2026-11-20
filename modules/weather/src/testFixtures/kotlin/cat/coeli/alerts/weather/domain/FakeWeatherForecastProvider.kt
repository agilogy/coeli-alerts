package cat.coeli.alerts.weather.domain

/** Returns always the same [forecast], whatever the city. */
class FakeWeatherForecastProvider(private val forecast: WeatherForecast?) :
    WeatherForecastProvider {
    override suspend fun forecast(city: String, days: Int): WeatherForecast? = forecast
}
