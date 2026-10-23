package com.dieka.wallet

import com.dieka.wallet.data.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ExpiryPolicyTest {
    private val card = BankCard.demos().first().copy(id = "real", expiry = "02/28")

    @Test fun monthEndHandlesLeapYearsShortMonthsAndYearBoundary() {
        assertEquals(LocalDate.of(2028, 2, 29), ExpiryPolicy.endDate("02/28"))
        assertEquals(LocalDate.of(2027, 2, 28), ExpiryPolicy.endDate("02/27"))
        assertEquals(LocalDate.of(2026, 4, 30), ExpiryPolicy.endDate("04/26"))
        assertEquals(LocalDate.of(2026, 12, 31), ExpiryPolicy.endDate("12/26"))
        listOf("", "00/26", "13/26", "2/28", "12/2028", "ab/cd").forEach { assertNull(ExpiryPolicy.endDate(it)) }
    }

    @Test fun remindersOnlyIncludeSevenThroughZeroDays() {
        val end = LocalDate.of(2028, 2, 29)
        listOf(8, 7, 1, 0, -1).forEach { days ->
            val result = ExpiryPolicy.upcoming(listOf(card), end.minusDays(days.toLong()))
            assertEquals(days in 0..7, result.isNotEmpty())
            if (result.isNotEmpty()) {
                assertEquals(days, result.single().days)
                assertEquals(if (days == 0) "今天到期" else "$days 天后到期", result.single().timing)
            }
        }
    }

    @Test fun equalExpiryRetainsCustomOrderAndIgnoresInvalidDates() {
        val cards = listOf(card.copy(id = "b"), card.copy(id = "a"), card.copy(id = "invalid", expiry = "13/28"))
        assertEquals(listOf("b", "a"), ExpiryPolicy.upcoming(cards, LocalDate.of(2028, 2, 22)).map { it.card.id })
        assertTrue(ExpiryPolicy.upcoming(cards, LocalDate.of(2028, 3, 1)).isEmpty())
    }
}
