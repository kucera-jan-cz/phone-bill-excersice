import org.junit.Test
import java.math.BigDecimal
import java.time.format.DateTimeParseException
import kotlin.test.assertEquals

class TelephoneBillCalculatorImplTest {
    private val calculator = TelephoneBillCalculatorImpl()

    @Test(expected = IllegalArgumentException::class)
    fun `invalid number`() {
        calculator.calculate("42077A4577453,13-01-2020 18:10:15,13-01-2020 18:12:57")
    }

    @Test(expected = DateTimeParseException::class)
    fun `invalid phone call timestamp`() {
        calculator.calculate("420774577453,13-01-2020 18:10:15.AA,13-01-2020 18:12:57")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `end before start`() {
        calculator.calculate("420774577453,13-01-2020 18:00:00,13-01-2020 17:00:00")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero second call`() {
        calculator.calculate("420774577453,13-01-2020 18:00:00,13-01-2020 18:00:00")
    }

    @Test
    fun `empty call log`() {
        assertEquals(calculator.calculate(""), BigDecimal.ZERO)
    }

    @Test
    fun `25 hour call`() {
        calculator.calculate("420774577453,13-01-2020 00:00:00,14-01-2020 01:00:00")
    }

    @Test
    fun `frequent hour call for 1 second`() {
        assertTime("13-01-2020 10:00:00", "13-01-2020 10:00:01", 1.0)
    }

    @Test
    fun `frequent hour call for 60 seconds`() {
        assertTime("13-01-2020 10:00:00", "13-01-2020 10:01:00", 1.0)
    }

    @Test
    fun `frequent hour call for 61 seconds`() {
        assertTime("13-01-2020 10:00:00", "13-01-2020 10:01:01", 2.0)
    }

    @Test
    fun `frequent hour call for 300 seconds`() {
        assertTime("13-01-2020 10:00:00", "13-01-2020 10:05:00", 5.0)
    }

    @Test
    fun `frequent hour call for 301 seconds`() {
        assertTime("13-01-2020 10:00:00", "13-01-2020 10:05:01", 5.2)
    }

    @Test
    fun `frequent hour call for 360 seconds`() {
        assertTime("13-01-2020 10:00:00", "13-01-2020 10:06:00", 5.2)
    }

    @Test
    fun `frequent hour call for 361 seconds`() {
        assertTime("13-01-2020 10:00:00", "13-01-2020 10:06:01", 5.4)
    }

    @Test
    fun `evening hour call for 1 second`() {
        assertTime("13-01-2020 00:00:00", "13-01-2020 00:00:01", 0.5)
    }

    @Test
    fun `evening hour call for 60 seconds`() {
        assertTime("13-01-2020 00:00:00", "13-01-2020 00:01:00", 0.5)
    }

    @Test
    fun `evening hour call for 61 seconds`() {
        assertTime("13-01-2020 00:00:00", "13-01-2020 00:01:01", 1.0)
    }

    @Test
    fun `evening hour call for 300 seconds`() {
        assertTime("13-01-2020 00:00:00", "13-01-2020 00:05:00", 2.5)
    }

    @Test
    fun `evening hour call for 301 seconds`() {
        assertTime("13-01-2020 00:00:00", "13-01-2020 00:05:01", 2.7)
    }

    @Test
    fun `evening hour call for 360 seconds`() {
        assertTime("13-01-2020 00:00:00", "13-01-2020 00:06:00", 2.7)
    }

    @Test
    fun `evening hour call for 361 seconds`() {
        assertTime("13-01-2020 00:00:00", "13-01-2020 00:06:01", 2.9)
    }

    @Test
    fun `transition between frequent and event hours for 301 seconds`() {
        //1st minute 0.5
        //2nd minute 0.5
        //3rd minute 1 - (starts 08:00:01)
        //4th minute 1
        //5th minute 1
        //6th minute 0.2
        assertTime("13-01-2020 07:58:00", "13-01-2020 08:03:01", 4.2)
    }

    @Test
    fun `filtering most-frequent-call`() {
        val callLog = """
420774577453,12-01-2020 08:00:00,12-01-2020 08:01:00
421774577453,13-01-2020 08:00:00,13-01-2020 08:05:00
421774577453,14-01-2020 08:00:00,14-01-2020 08:05:00
421774577453,15-01-2020 08:00:00,15-01-2020 08:05:00
422774577453,13-01-2020 08:00:00,13-01-2020 08:10:00
422774577453,14-01-2020 08:00:00,14-01-2020 08:10:00
        """.trimIndent()
        // Fist number calls for 1 minute - 1
        // Second number calls 3 times for 5 minutes -> free because it's highest
        // Third number calls 2 times for 10 minutes -> (5 + 0.2 * 5) * 2
        assertEquals(
            BigDecimal.valueOf(13).setScale(2),
            calculator.calculate(callLog).setScale(2)
        )
    }

    @Test
    fun `filtering most-frequent-call order by number`() {
        val callLog = """
420774577453,12-01-2020 08:00:00,12-01-2020 08:01:00
421774577453,13-01-2020 08:00:00,13-01-2020 08:05:00
421774577453,13-01-2020 08:00:00,13-01-2020 08:05:00
422774577453,13-01-2020 08:00:00,13-01-2020 08:10:00
422774577453,14-01-2020 08:00:00,14-01-2020 08:10:00
        """.trimIndent()
        // Fist number calls for 1 minute - 1
        // Second number calls 2 times for 5 minutes -> 10*1
        // Third number calls 2 times for 10 minutes -> free because it's highest
        assertEquals(
            BigDecimal.valueOf(11).setScale(2),
            calculator.calculate(callLog).setScale(2)
        )
    }

    private fun assertTime(start: String, end: String, price: Double) {
        assertEquals(
            BigDecimal.valueOf(price).setScale(2),
            calculator.calculate("""
            421774577453,${start},${end}
            420774577453,13-01-2020 01:00:00,13-01-2020 01:10:00
            420774577453,13-01-2020 03:00:00,13-01-2020 03:10:00
            """.trimIndent()).setScale(2)
        )
    }
}
