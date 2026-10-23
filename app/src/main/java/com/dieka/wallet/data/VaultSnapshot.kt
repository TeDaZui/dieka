package com.dieka.wallet.data

import java.util.UUID

data class TrashEntry(
    val id: String = UUID.randomUUID().toString(),
    val card: BankCard,
    val deletedAt: Long,
    val originalIndex: Int,
    val previousId: String? = null,
    val nextId: String? = null,
) {
    val recoverUntil: Long get() = deletedAt + RETENTION_MS
    fun recoverable(now: Long) = now < recoverUntil
    companion object { const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000 }
}

data class VaultSnapshot(val cards: List<BankCard> = emptyList(), val trash: List<TrashEntry> = emptyList()) {
    fun cleaned(now: Long) = copy(trash = trash.filter { it.recoverable(now) })

    fun remove(cardId: String, now: Long): Pair<VaultSnapshot, TrashEntry>? {
        val index = cards.indexOfFirst { it.id == cardId }
        if (index < 0) return null
        val entry = TrashEntry(card = cards[index], deletedAt = now, originalIndex = index,
            previousId = cards.getOrNull(index - 1)?.id, nextId = cards.getOrNull(index + 1)?.id)
        return copy(cards = cards.filterNot { it.id == cardId }, trash = cleaned(now).trash + entry) to entry
    }

    fun restore(entryId: String, now: Long): VaultSnapshot {
        val entry = trash.firstOrNull { it.id == entryId } ?: error("这张卡片已不在回收站")
        require(entry.recoverable(now)) { "已超过七天，无法恢复这张卡片" }
        require(cards.none { it.number == entry.card.number || it.id == entry.card.id }) { "卡包中已有相同卡号，无法重复恢复" }
        val next = cards.indexOfFirst { it.id == entry.nextId }
        val previous = cards.indexOfFirst { it.id == entry.previousId }
        val insertion = when {
            next >= 0 -> next
            previous >= 0 -> previous + 1
            else -> entry.originalIndex.coerceIn(0, cards.size)
        }
        return copy(cards = cards.toMutableList().apply { add(insertion, entry.card) },
            trash = cleaned(now).trash.filterNot { it.id == entryId })
    }
}
