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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.LongColumnType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.statements.jdbc.JdbcResult
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

private val logger = KotlinLogging.logger {}

class PostgresAlertRepository(
    private val database: Database,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AlertRepository {

    override suspend fun record(alert: Alert) {
        logger.atDebug {
            message = "Recording alert"
            payload = mapOf("alertId" to alert.id.value, "alertLevel" to alert.level)
        }
        inTransaction {
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
        val alerts = inTransaction {
            AlertsTable.selectAll()
                .where { AlertsTable.level inList minimumLevel.atLeastAsSevere }
                .orderBy(AlertsTable.occurredAt to SortOrder.DESC, AlertsTable.id to SortOrder.ASC)
                .limit(pagination.size)
                .offset(pagination.offset)
                .map { it.toAlert() }
        }
        logger.atTrace {
            message = "Found alerts"
            payload = mapOf("count" to alerts.size)
        }
        return alerts
    }

    // Alternative to search() in plain SQL instead of Exposed's DSL.
    internal suspend fun search2(minimumLevel: AlertLevel, pagination: Pagination): List<Alert> =
        inTransaction {
            val levels = minimumLevel.atLeastAsSevere
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
                ) { resultSet ->
                    val fieldsIndex =
                        fieldsIndexOf(
                            AlertsTable.id,
                            AlertsTable.level,
                            AlertsTable.message,
                            AlertsTable.occurredAt,
                        )
                    JdbcResult(resultSet).use { result ->
                        buildList<Alert> {
                            while (resultSet.next()) {
                                add(ResultRow.create(result, fieldsIndex).toAlert())
                            }
                        }
                    }
                }
                .orEmpty()
        }

    private suspend fun <T> inTransaction(block: JdbcTransaction.() -> T): T =
        withContext(ioDispatcher) { suspendTransaction(database) { block() } }

    private fun ResultRow.toAlert(): Alert =
        Alert(
            id = AlertId(this[AlertsTable.id]),
            level = this[AlertsTable.level],
            message = this[AlertsTable.message],
            occurredAt = this[AlertsTable.occurredAt].toInstant().toKotlinInstant(),
        )
}
