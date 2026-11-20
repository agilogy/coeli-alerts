package cat.coeli.alerts.alertsrepository.infrastructure.postgres

import cat.coeli.alerts.alertsrepository.domain.Alert
import cat.coeli.alerts.alertsrepository.domain.AlertId
import cat.coeli.alerts.alertsrepository.domain.AlertLevel
import cat.coeli.alerts.alertsrepository.domain.AlertRepository
import cat.coeli.alerts.alertsrepository.domain.Pagination
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.ZoneOffset
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.LongColumnType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.mappers.R2dbcRegistryTypeMapping
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcRow
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

private val logger = KotlinLogging.logger {}

class PostgresAlertRepository(private val database: R2dbcDatabase) : AlertRepository {

    override suspend fun record(alert: Alert) {
        logger.atDebug {
            message = "Recording alert"
            payload = mapOf("alertId" to alert.id.value, "alertLevel" to alert.level)
        }
        suspendTransaction(database) {
            AlertsTable.insert { row ->
                row[id] = alert.id.value
                row[level] = alert.level
                row[message] = alert.message
                row[occurredAt] = alert.occurredAt.toJavaInstant().atOffset(ZoneOffset.UTC)
            }
        }
    }

    override suspend fun search(minimumLevel: AlertLevel, pagination: Pagination): List<Alert> {
        logger.atDebug {
            message = "Searching alerts"
            payload =
                mapOf(
                    "minimumLevel" to minimumLevel,
                    "offset" to pagination.offset,
                    "size" to pagination.size,
                )
        }
        val alerts =
            suspendTransaction(database) {
                AlertsTable.selectAll()
                    .where { AlertsTable.level inList minimumLevel.atLeastAsSevere }
                    .orderBy(
                        AlertsTable.occurredAt to SortOrder.DESC,
                        AlertsTable.id to SortOrder.ASC,
                    )
                    .limit(pagination.size)
                    .offset(pagination.offset)
                    .map { it.toAlert() }
                    .toList()
            }
        logger.atTrace {
            message = "Found alerts"
            payload = mapOf("count" to alerts.size)
        }
        return alerts
    }

    // Alternative to search() in plain SQL instead of Exposed's DSL.
    internal suspend fun search2(minimumLevel: AlertLevel, pagination: Pagination): List<Alert> =
        suspendTransaction(database) {
            val levels = minimumLevel.atLeastAsSevere
            val fieldsIndex =
                fieldsIndexOf(
                    AlertsTable.id,
                    AlertsTable.level,
                    AlertsTable.message,
                    AlertsTable.occurredAt,
                )
            exec(
                    """
                        SELECT id, level, message, occurred_at
                        FROM alerts
                        WHERE level IN (${levels.joinToString { "?" }})
                        ORDER BY occurred_at DESC, id ASC
                        LIMIT ? OFFSET ?
                    """
                        .trimIndent(),
                    args =
                        levels.map { VarCharColumnType() to it.name } +
                            listOf(
                                IntegerColumnType() to pagination.size,
                                LongColumnType() to pagination.offset,
                            ),
                ) { row ->
                    ResultRow.create(R2dbcRow(row, R2dbcRegistryTypeMapping.default()), fieldsIndex)
                        .toAlert()
                }
                ?.run { filterNotNull().toList() }
                .orEmpty()
        }

    private fun ResultRow.toAlert(): Alert =
        Alert(
            id = AlertId(this[AlertsTable.id]),
            level = this[AlertsTable.level],
            message = this[AlertsTable.message],
            occurredAt = this[AlertsTable.occurredAt].toInstant().toKotlinInstant(),
        )
}
