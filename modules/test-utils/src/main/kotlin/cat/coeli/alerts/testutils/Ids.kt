package cat.coeli.alerts.testutils

import kotlin.uuid.Uuid

fun testUuid(id: Int): Uuid = Uuid.fromLongs(0, id.toLong())
