package cat.coeli.alerts.alertsrepository.domain

interface AlertRepository {
    /** Records a new alert. Fails with an exception if an alert with the same id already exists. */
    suspend fun record(alert: Alert)

    /**
     * Alerts whose level is [minimumLevel] or more severe, most recent first (ties broken by id),
     * restricted to the page described by [pagination].
     */
    suspend fun search(minimumLevel: AlertLevel, pagination: Pagination): List<Alert>
}
