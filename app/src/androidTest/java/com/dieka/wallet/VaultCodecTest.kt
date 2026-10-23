package com.dieka.wallet

import com.dieka.wallet.data.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class VaultCodecTest {
    @Test fun appLinkIsOptionalInOldRecordsAndPersistsInNewSnapshots() {
        val card = BankCard.demos().first()
        assertNull(CardCodec.decode(CardCodec.encode(card)).appLink)
        val linked = card.copy(appLink = AppLink("test.bank", "test.bank.Main", "银行 APP", "我的银行"))
        assertEquals(linked, CardCodec.decode(CardCodec.encode(linked)))
        assertEquals(listOf(linked), VaultCodec.decode(VaultCodec.encode(VaultSnapshot(listOf(linked)))).cards)
    }
    @Test fun legacyArrayLoadsWithoutDroppingIndependentDesignOrImage() {
        val cards = BankCard.demos().take(2).map { it.copy(cover = "image-bytes") }
        val legacy = JSONArray().apply { cards.forEach { put(CardCodec.encode(it)) } }.toString()
        val snapshot = VaultCodec.decode(legacy)
        assertEquals(cards, snapshot.cards)
        assertTrue(snapshot.trash.isEmpty())
        val migrated = JSONObject(VaultCodec.encode(snapshot))
        assertEquals(2, migrated.getInt("version"))
        assertEquals(snapshot, VaultCodec.decode(migrated.toString()))
    }

    @Test fun snapshotRoundTripPreservesTrashAndNeighborMetadata() {
        val (snapshot, _) = VaultSnapshot(BankCard.demos()).remove("demo-2", 1_800_000_000_000L)!!
        assertEquals(snapshot, VaultCodec.decode(VaultCodec.encode(snapshot)))
    }

    @Test fun unknownVersionAndMalformedDataAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { VaultCodec.decode("""{"version":99,"cards":[],"trash":[]}""") }
        assertThrows(Exception::class.java) { VaultCodec.decode("""{"version":2,"cards":[{}],"trash":[]}""") }
    }
}
