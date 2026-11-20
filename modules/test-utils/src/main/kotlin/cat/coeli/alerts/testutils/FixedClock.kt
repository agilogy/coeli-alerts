package cat.coeli.alerts.testutils

import kotlin.time.Clock
import kotlin.time.Instant

class FixedClock(private val now: Instant) : Clock {
    override fun now(): Instant = now
}
