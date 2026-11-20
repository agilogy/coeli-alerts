package cat.coeli.alerts.weatheralertservice.domain

import kotlin.time.Clock
import kotlin.time.Instant

class FixedClock(private val now: Instant) : Clock {
    override fun now(): Instant = now
}
