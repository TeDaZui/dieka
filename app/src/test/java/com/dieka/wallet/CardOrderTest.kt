package com.dieka.wallet

import com.dieka.wallet.data.BankCard
import com.dieka.wallet.data.CardOrder
import org.junit.Assert.*
import org.junit.Test

class CardOrderTest {
    private val cards = listOf(
        BankCard(id = "a", bank = "One", title = "Daily", number = "4111111111111111", cover = "custom-cover"),
        BankCard(id = "b", bank = "Two", title = "Travel", number = "5555555555554444")
    )

    @Test fun sortingPreservesCurrentCardDataAndIdentity() {
        val result = CardOrder.apply(cards, listOf("b", "a"))!!
        assertSame(cards[1], result[0])
        assertSame(cards[0], result[1])
        assertEquals("custom-cover", result[1].cover)
    }

    @Test fun staleOrDuplicateIdsCannotDropOrDuplicateCards() {
        assertNull(CardOrder.apply(cards, listOf("a", "a")))
        assertNull(CardOrder.apply(cards, listOf("a")))
        assertNull(CardOrder.apply(cards, listOf("b", "c")))
        assertNull(CardOrder.apply(cards, listOf("b", "a", "c")))
    }

    @Test fun emptyAndUnchangedOrdersAreValid() {
        assertEquals(emptyList<BankCard>(), CardOrder.apply(emptyList(), emptyList()))
        assertEquals(cards, CardOrder.apply(cards, listOf("a", "b")))
    }
}
