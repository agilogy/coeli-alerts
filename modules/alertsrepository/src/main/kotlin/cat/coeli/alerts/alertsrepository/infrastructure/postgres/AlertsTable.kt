package cat.coeli.alerts.alertsrepository.infrastructure.postgres

import cat.coeli.alerts.alertsrepository.domain.AlertLevel
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

private const val LEVEL_MAX_LENGTH = 16

internal object AlertsTable : Table("alerts") {
    val id = uuid("id")
    val level = enumerationByName<AlertLevel>("level", LEVEL_MAX_LENGTH)
    val message = text("message")
    val occurredAt = timestampWithTimeZone("occurred_at")

    override val primaryKey = PrimaryKey(id)
}
