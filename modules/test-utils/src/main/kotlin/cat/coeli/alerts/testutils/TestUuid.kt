package cat.coeli.alerts.testutils

import kotlin.uuid.Uuid

fun testUuid(id: Int): Uuid =
    Uuid.fromLongs(mostSignificantBits = 0, leastSignificantBits = id.toLong())
