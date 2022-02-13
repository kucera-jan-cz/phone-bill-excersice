import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

val FREQUENT_HOUR_MINUTE_PRICE: BigDecimal = BigDecimal.ONE
val NIGHT_HOUR_MINUTE_PRICE: BigDecimal = BigDecimal.valueOf(0.5)
val LONG_CALL_MINUTE_PRICE: BigDecimal = BigDecimal.valueOf(0.2)
val SIXTY_SECONDS: BigDecimal = BigDecimal.valueOf(60)
val LONG_CALL_MINUTES = 5L

class TelephoneBillCalculatorImpl : TelephoneBillCalculator {
    private val datetimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")
    private val phoneNumberRegex = Regex("""\d+""")
    private val longCallDuration = Duration.ofMinutes(LONG_CALL_MINUTES)
    private val frequentHours = LocalTime.of(8, 0, 0)..LocalTime.of(15, 59, 59)
    override fun calculate(phoneLog: String): BigDecimal {
        if (phoneLog.isEmpty()) return BigDecimal.ZERO
        val entries = parsePhoneLog(phoneLog)
        val mostCalledNumber = findMostCalledNumber(entries)
        return calculatePhoneLogCost(entries, mostCalledNumber)
    }

    private fun calculatePhoneLogCost(entries: List<LogEntry>, mostCalledNumber: String): BigDecimal {
        return entries
            .filter { it.phoneNumber != mostCalledNumber }
            .fold(BigDecimal.ZERO) { currentCost, entry -> currentCost.plus(calculateLogEntryCost(entry)) }
    }

    private fun calculateLogEntryCost(entry: LogEntry): BigDecimal {
        val callRange = entry.start..entry.end
        // Initial cost - depends on how in which time call starts
        var cost = if (entry.start.toLocalTime() in frequentHours) FREQUENT_HOUR_MINUTE_PRICE else NIGHT_HOUR_MINUTE_PRICE

        (1 until LONG_CALL_MINUTES).forEach {
            val startingCallMinute = entry.start.plusSeconds(1).plusMinutes(it.toLong())
            if (startingCallMinute in callRange) {
                cost = if (startingCallMinute.toLocalTime() in frequentHours) {
                    cost.plus(FREQUENT_HOUR_MINUTE_PRICE)
                } else {
                    cost.plus(NIGHT_HOUR_MINUTE_PRICE)
                }
            }
        }

        // If call is longer than 5 minutes, apply discount for long call
        val callDurationInSeconds = Duration.ofSeconds(ChronoUnit.SECONDS.between(entry.start, entry.end))
        if (longCallDuration < callDurationInSeconds) {
            val callMinutes = BigDecimal.valueOf(callDurationInSeconds.seconds).divide(SIXTY_SECONDS, RoundingMode.CEILING).minus(BigDecimal.valueOf(LONG_CALL_MINUTES))
            cost = cost.plus(LONG_CALL_MINUTE_PRICE.multiply(callMinutes))
        }
        return cost
    }

    private fun findMostCalledNumber(entries: List<LogEntry>): String {
        return entries.groupBy { it.phoneNumber }.entries.sortedWith(compareBy({ it.value.size }, { it.key.toLong() })).last().key
    }

    private fun parsePhoneLog(phoneLog: String): List<LogEntry> {
        return phoneLog.lines().map { line ->
            val (phoneNumber, startAsText, endAsText) = line.split(",", limit = 3)
            require(phoneNumberRegex.matches(phoneNumber)) { "Phone number contains invalid characters" }

            val start = LocalDateTime.from(datetimeFormatter.parse(startAsText))
            val end = LocalDateTime.from(datetimeFormatter.parse(endAsText))
            require(start.isBefore(end)) { "Phone call start dates after or equal to phone call end" }
            LogEntry(phoneNumber, start, end)
        }
    }
}

private data class LogEntry(val phoneNumber: String, val start: LocalDateTime, val end: LocalDateTime)
