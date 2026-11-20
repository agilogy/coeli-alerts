package cat.coeli.alerts.weatheralertservice

import cat.coeli.alerts.alertsrepository.infrastructure.postgres.PostgresAlertRepository
import cat.coeli.alerts.weather.infrastructure.openmeteo.OpenMeteoWeatherForecastProvider
import cat.coeli.alerts.weather.infrastructure.openmeteo.openMeteoHttpClient
import cat.coeli.alerts.weatheralertservice.domain.WeatherAlertService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.Clock
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase

private val logger = KotlinLogging.logger {}

suspend fun main() {
    val jdbcUrl = environmentVariable("DATABASE_URL")
    val user = environmentVariable("DATABASE_USER")
    val password = environmentVariable("DATABASE_PASSWORD")
    migrateDatabase(jdbcUrl, user, password)
    // The same database, through a pool of non-blocking R2DBC connections.
    val database =
        R2dbcDatabase.connect(
            url = jdbcUrl.replaceFirst(oldValue = "jdbc:", newValue = "r2dbc:pool:"),
            user = user,
            password = password,
        )
    openMeteoHttpClient().use { httpClient ->
        WeatherAlertService(
                forecastProvider = OpenMeteoWeatherForecastProvider(httpClient),
                alertRepository = PostgresAlertRepository(database),
                clock = Clock.System,
            )
            .run("Barcelona")
            .forEach { alert ->
                logger.atInfo {
                    message = "Recent alert"
                    payload =
                        mapOf(
                            "occurredAt" to alert.occurredAt,
                            "alertLevel" to alert.level,
                            "alertId" to alert.id.value,
                            "alertMessage" to alert.message,
                        )
                }
            }
    }
}

// Flyway only works with JDBC.
private fun migrateDatabase(jdbcUrl: String, user: String, password: String) {
    val migrations = Flyway.configure().dataSource(jdbcUrl, user, password).load().migrate()
    logger.atInfo {
        message = "Applied database migrations"
        payload = mapOf("migrationsExecuted" to migrations.migrationsExecuted)
    }
}

private fun environmentVariable(name: String): String =
    requireNotNull(System.getenv(name)) { "Missing environment variable $name" }
