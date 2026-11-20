package cat.coeli.alerts.app.domain

import kotlin.time.Clock
import kotlin.time.Instant

/** A clock stopped at [now]. */
class FixedClock(private val now: Instant) : Clock {
    override fun now(): Instant = now
}
