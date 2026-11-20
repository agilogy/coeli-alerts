package cat.coeli.alerts.core

import kotlin.test.Test
import kotlin.test.assertEquals

class GreeterTest {
    @Test
    fun greetsByName() {
        val greeter = Greeter("Coeli")

        val result = greeter.greet()

        assertEquals("Hello, Coeli!", result)
    }
}
