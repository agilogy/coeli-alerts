package cat.coeli.alerts.alertsrepository.domain

import kotlin.time.Instant
import kotlin.uuid.Uuid

@JvmInline value class AlertId(val value: Uuid)

/** Declaration order is severity order: each level is more severe than the previous one. */
enum class AlertLevel {
    INFO,
    WARNING,
    ERROR,
    CRITICAL;

    internal val atLeastAsSevere: Set<AlertLevel>
        get() = entries.filter { it >= this }.toSet()
}

data class Alert(
    val id: AlertId,
    val level: AlertLevel,
    val message: String,
    val occurredAt: Instant,
)
