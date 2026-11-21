package cat.coeli.alerts.testutils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class FixedClockTest {

    @Test
    fun nowAlwaysReturnsTheFixedInstant() {
        val instant = Instant.parse("2026-11-21T10:00:00Z")
        val clock = FixedClock(instant)

        val readings = listOf(clock.now(), clock.now())

        assertEquals(listOf(instant, instant), readings)
    }
}
