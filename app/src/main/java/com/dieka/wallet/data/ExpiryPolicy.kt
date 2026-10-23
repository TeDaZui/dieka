package com.dieka.wallet.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

data class ExpiringCard(val card: BankCard, val days: Int) {
    val timing: String get() = if (days == 0) "今天到期" else "$days 天后到期"
}

object ExpiryPolicy {
    fun endDate(expiry: String): LocalDate? {
        if (!Regex("(0[1-9]|1[0-2])/[0-9]{2}").matches(expiry)) return null
        return YearMonth.of(2000 + expiry.takeLast(2).toInt(), expiry.take(2).toInt()).atEndOfMonth()
    }
    fun upcoming(cards: List<BankCard>, today: LocalDate): List<ExpiringCard> = cards.mapNotNull { card ->
        val end = endDate(card.expiry) ?: return@mapNotNull null
        val days = ChronoUnit.DAYS.between(today, end).toInt()
        if (days in 0..7) ExpiringCard(card, days) else null
    }.sortedBy { it.days } // Stable sort retains the user's order for matching dates.
}
