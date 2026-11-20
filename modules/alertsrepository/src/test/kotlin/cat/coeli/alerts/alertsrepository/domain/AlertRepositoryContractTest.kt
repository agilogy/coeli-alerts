package cat.coeli.alerts.alertsrepository.domain

import cat.coeli.alerts.testutils.testUuid
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/**
 * What every [AlertRepository] must do. Each implementation has a subclass that says how to create
 * it, and runs all these tests against it.
 */
abstract class AlertRepositoryContractTest {

    /** A new, empty repository for each test. */
    protected abstract fun newRepository(): AlertRepository

    private lateinit var repository: AlertRepository

    @BeforeTest
    fun createRepository() {
        repository = newRepository()
    }

    @Test
    fun searchReturnsRecordedAlertsMostRecentFirst() = runTest {
        val first = alert(id = 1, occurredAt = "2026-11-20T10:00:00Z")
        val second = alert(id = 2, occurredAt = "2026-11-20T11:00:00Z")
        val third = alert(id = 3, occurredAt = "2026-11-20T12:00:00Z")
        listOf(second, third, first).forEach { repository.record(it) }

        val result = repository.search(AlertLevel.INFO, Pagination(offset = 0, size = 10))

        assertEquals(listOf(third, second, first), result)
    }

    @Test
    fun searchBreaksTiesInOccurredAtById() = runTest {
        val alertA = alert(id = 1, occurredAt = "2026-11-20T10:00:00Z")
        val alertB = alert(id = 2, occurredAt = "2026-11-20T10:00:00Z")
        listOf(alertB, alertA).forEach { repository.record(it) }

        val result = repository.search(AlertLevel.INFO, Pagination(offset = 0, size = 10))

        assertEquals(listOf(alertA, alertB), result)
    }

    @Test
    fun searchReturnsOnlyAlertsAtLeastAsSevereAsTheMinimumLevel() = runTest {
        val info = alert(id = 1, level = AlertLevel.INFO, occurredAt = "2026-11-20T10:00:00Z")
        val warning = alert(id = 2, level = AlertLevel.WARNING, occurredAt = "2026-11-20T11:00:00Z")
        val error = alert(id = 3, level = AlertLevel.ERROR, occurredAt = "2026-11-20T12:00:00Z")
        val critical =
            alert(id = 4, level = AlertLevel.CRITICAL, occurredAt = "2026-11-20T13:00:00Z")
        listOf(info, warning, error, critical).forEach { repository.record(it) }

        val result = repository.search(AlertLevel.WARNING, Pagination(offset = 0, size = 10))

        assertEquals(listOf(critical, error, warning), result)
    }

    @Test
    fun searchReturnsTheRequestedPage() = runTest {
        val alerts =
            (0..4).map { hour -> alert(id = hour, occurredAt = "2026-11-20T1$hour:00:00Z") }
        alerts.forEach { repository.record(it) }

        val result = repository.search(AlertLevel.INFO, Pagination(offset = 1, size = 2))

        assertEquals(listOf(alerts[3], alerts[2]), result)
    }

    @Test
    fun searchReturnsNothingWhenTheOffsetIsBeyondTheLastAlert() = runTest {
        repository.record(alert(id = 1, occurredAt = "2026-11-20T10:00:00Z"))

        val result = repository.search(AlertLevel.INFO, Pagination(offset = 1, size = 10))

        assertEquals(emptyList(), result)
    }

    @Test
    fun recordingAnAlertWithAnExistingIdFails() = runTest {
        val existing =
            alert(id = 1, message = "Disk almost full", occurredAt = "2026-11-20T10:00:00Z")
        repository.record(existing)

        assertFails {
            repository.record(
                alert(id = 1, message = "Another alert", occurredAt = "2026-11-20T11:00:00Z"),
            )
        }

        assertEquals(
            listOf(existing),
            repository.search(AlertLevel.INFO, Pagination(offset = 0, size = 10)),
        )
    }

    private fun alert(
        id: Int,
        occurredAt: String,
        level: AlertLevel = AlertLevel.ERROR,
        message: String = "Alert $id",
    ): Alert =
        Alert(
            id = AlertId(testUuid(id)),
            level = level,
            message = message,
            occurredAt = Instant.parse(occurredAt),
        )
}
