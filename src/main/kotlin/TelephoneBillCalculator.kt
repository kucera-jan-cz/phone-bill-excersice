import java.math.BigDecimal

interface TelephoneBillCalculator {
    fun calculate(phoneLog: String): BigDecimal
}
