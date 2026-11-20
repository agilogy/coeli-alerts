package cat.coeli.alerts.weather.infrastructure.openmeteo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class GeocodingResponse(val results: List<GeocodingResult> = emptyList())

@Serializable
internal data class GeocodingResult(val name: String, val latitude: Double, val longitude: Double)

@Serializable internal data class ForecastResponse(val daily: DailyResponse)

@Serializable
internal data class DailyResponse(
    val time: List<String>,
    @SerialName("temperature_2m_min") val minTemperatures: List<Double>,
    @SerialName("temperature_2m_max") val maxTemperatures: List<Double>,
    @SerialName("precipitation_sum") val precipitationSums: List<Double>,
)
