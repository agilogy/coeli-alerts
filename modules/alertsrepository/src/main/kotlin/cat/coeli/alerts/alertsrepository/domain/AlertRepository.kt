package cat.coeli.alerts.alertsrepository.domain

interface AlertRepository {
    /** Fails if an alert with the same id already exists. */
    suspend fun record(alert: Alert)

    /** Most recent first; alerts with the same occurredAt are ordered by id. */
    suspend fun search(minimumLevel: AlertLevel, pagination: Pagination): List<Alert>
}
