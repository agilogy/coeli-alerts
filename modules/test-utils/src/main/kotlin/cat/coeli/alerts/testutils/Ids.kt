package cat.coeli.alerts.testutils

import kotlin.uuid.Uuid

/**
 * A readable, deterministic UUID for tests: `testUuid(1)` is 00000000-0000-0000-0000-000000000001.
 */
fun testUuid(id: Int): Uuid = Uuid.fromLongs(0, id.toLong())
