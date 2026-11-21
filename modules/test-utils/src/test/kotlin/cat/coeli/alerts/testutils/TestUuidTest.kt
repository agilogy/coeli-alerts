package cat.coeli.alerts.testutils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class TestUuidTest {

    @Test
    fun testUuidEndsWithTheIdInHexadecimal() {
        val uuid = testUuid(26)

        assertEquals(Uuid.parse("00000000-0000-0000-0000-00000000001a"), uuid)
    }

    @Test
    fun testUuidReturnsTheSameUuidForTheSameId() {
        val first = testUuid(1)

        val second = testUuid(1)

        assertEquals(first, second)
    }
}
