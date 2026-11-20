package cat.coeli.alerts.alertsrepository.domain

/** Keeps the alerts in memory, with the same behaviour as the real repository. */
class InMemoryAlertRepository : AlertRepository {
    var alerts: List<Alert> = emptyList()
        private set

    override suspend fun record(alert: Alert) {
        require(alerts.none { it.id == alert.id }) { "Alert ${alert.id} already exists" }
        alerts = alerts + alert
    }

    override suspend fun search(minimumLevel: AlertLevel, pagination: Pagination): List<Alert> =
        alerts
            .filter { it.level >= minimumLevel }
            .sortedWith(
                compareByDescending<Alert> { it.occurredAt }.thenBy { it.id.value.toString() },
            )
            .drop(pagination.offset.toInt())
            .take(pagination.size)
}
