package cat.coeli.alerts.weather.domain

class FakeWeatherForecastProvider(private val forecast: WeatherForecast?) :
    WeatherForecastProvider {
    override suspend fun forecast(city: String, days: Int): WeatherForecast? = forecast
}
