package com.dieka.wallet.data

object CardOrder {
    /** Resolve IDs against the current records, never against copies from the sorting UI. */
    fun apply(cards: List<BankCard>, ids: List<String>): List<BankCard>? {
        val byId = cards.associateBy { it.id }
        if (ids.size != cards.size || ids.toSet().size != ids.size || ids.toSet() != byId.keys) return null
        return ids.map { byId.getValue(it) }
    }
}
