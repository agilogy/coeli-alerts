package cat.coeli.alerts.app

import cat.coeli.alerts.alertsrepository.domain.Alert
import cat.coeli.alerts.alertsrepository.domain.AlertLevel
import cat.coeli.alerts.alertsrepository.infrastructure.postgres.PostgresAlertRepository
import cat.coeli.alerts.app.domain.WeatherAlertService
import cat.coeli.alerts.weather.infrastructure.openmeteo.OpenMeteoWeatherForecastProvider
import cat.coeli.alerts.weather.infrastructure.openmeteo.openMeteoHttpClient
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KotlinLogging
import javax.sql.DataSource
import kotlin.time.Clock
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database

private val logger = KotlinLogging.logger {}

/** Builds the weather alert service with its real dependencies, runs it and prints the result. */
suspend fun main() {
    dataSourceFromEnvironment().use { dataSource ->
        migrateDatabase(dataSource)
        openMeteoHttpClient().use { httpClient ->
            WeatherAlertService(
                    forecastProvider = OpenMeteoWeatherForecastProvider(httpClient),
                    alertRepository = PostgresAlertRepository(Database.connect(dataSource)),
                    clock = Clock.System,
                )
                .run("Barcelona")
                .forEach { println(format(it)) }
        }
    }
}

private val levelColumnWidth = AlertLevel.entries.maxOf { it.name.length }

private fun format(alert: Alert): String =
    "${alert.occurredAt}  ${alert.level.name.padEnd(levelColumnWidth)}  ${alert.id.value}  ${alert.message}"

private fun dataSourceFromEnvironment(): HikariDataSource =
    HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = environmentVariable("DATABASE_URL")
            username = environmentVariable("DATABASE_USER")
            password = environmentVariable("DATABASE_PASSWORD")
        },
    )

/** Applies the pending scripts found in classpath:db/migration of every module. */
private fun migrateDatabase(dataSource: DataSource) {
    val migrations = Flyway.configure().dataSource(dataSource).load().migrate()
    logger.atInfo {
        message = "Applied database migrations"
        payload = mapOf("migrationsExecuted" to migrations.migrationsExecuted)
    }
}

private fun environmentVariable(name: String): String =
    requireNotNull(System.getenv(name)) { "Missing environment variable $name" }
