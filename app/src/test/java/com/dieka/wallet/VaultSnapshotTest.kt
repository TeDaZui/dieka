package com.dieka.wallet

import com.dieka.wallet.data.*
import org.junit.Assert.*
import org.junit.Test

class VaultSnapshotTest {
    private val cards = BankCard.demos()
    private val time = 1_800_000_000_000L

    @Test fun removeAndUndoPreserveAllDataAndOriginalOrder() {
        val original = VaultSnapshot(cards)
        val (removed, entry) = original.remove(cards[2].id, time)!!
        assertEquals(cards[2], entry.card)
        assertEquals(cards[1].id, entry.previousId)
        assertEquals(cards[3].id, entry.nextId)
        assertEquals(original, removed.restore(entry.id, time + 1))
    }

    @Test fun consecutiveDeletesRemainIndependentAndRestoreByNeighbors() {
        val (first, a) = VaultSnapshot(cards).remove(cards[1].id, time)!!
        val (second, b) = first.remove(cards[2].id, time + 1)!!
        assertEquals(2, second.trash.size)
        val restored = second.restore(b.id, time + 2).restore(a.id, time + 3)
        assertEquals(cards, restored.cards)
        assertTrue(restored.trash.isEmpty())
    }

    @Test fun restorationUsesNeighborThenIndexThenEnd() {
        val (removed, entry) = VaultSnapshot(cards).remove(cards[2].id, time)!!
        val reordered = removed.copy(cards = removed.cards.reversed())
        val restored = reordered.restore(entry.id, time)
        assertEquals(restored.cards.indexOf(cards[3]) - 1, restored.cards.indexOf(cards[2]))
        assertEquals(2, removed.copy(cards = listOf(cards[0], cards[4], cards[5])).restore(entry.id, time).cards.indexOf(cards[2]))
        assertEquals(listOf(cards[0], cards[2]), removed.copy(cards = listOf(cards[0])).restore(entry.id, time).cards)
        assertEquals(listOf(cards[1], cards[2]), removed.copy(cards = listOf(cards[1])).restore(entry.id, time).cards)
    }

    @Test fun sevenDaysIsAnExactExclusiveDeadlineAndRedeleteStartsAgain() {
        val (removed, entry) = VaultSnapshot(cards).remove(cards[1].id, time)!!
        val deadline = time + 7L * 24 * 60 * 60 * 1000
        assertTrue(entry.recoverable(deadline - 1))
        assertFalse(entry.recoverable(deadline))
        assertThrows(IllegalArgumentException::class.java) { removed.restore(entry.id, deadline) }
        assertTrue(removed.cleaned(deadline).trash.isEmpty())
        val (again, next) = removed.restore(entry.id, deadline - 1).remove(cards[1].id, deadline - 1)!!
        assertNotEquals(entry.id, next.id)
        assertEquals(deadline - 1 + TrashEntry.RETENTION_MS, next.recoverUntil)
        assertEquals(listOf(next), again.trash)
    }

    @Test fun duplicateRestorationCannotDropTrashOrOverwriteActiveCard() {
        val (removed, entry) = VaultSnapshot(cards).remove(cards[1].id, time)!!
        val duplicate = entry.card.copy(id = "new-id", title = "Replacement")
        val snapshot = removed.copy(cards = removed.cards + duplicate)
        assertThrows(IllegalArgumentException::class.java) { snapshot.restore(entry.id, time) }
        assertEquals(entry, snapshot.trash.single())
        assertEquals("Replacement", snapshot.cards.last().title)
    }
}
