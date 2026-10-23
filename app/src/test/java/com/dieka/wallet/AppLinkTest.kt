package com.dieka.wallet

import com.dieka.wallet.data.*
import com.dieka.wallet.ui.TouchEvent
import com.dieka.wallet.ui.WalletHaptics
import org.junit.Assert.*
import org.junit.Test

class AppLinkTest {
    @Test fun shortcutUsesDetectedNameUnlessUserOverridesIt() {
        val link = AppLink("test.bank", "test.Main", "银行应用")
        assertEquals("银行应用", link.displayName)
        assertEquals("工资卡", link.copy(customName = " 工资卡 ").displayName)
        assertEquals("银行应用", link.copy(customName = "  ").displayName)
    }
    @Test fun associationSurvivesTrashAndRecoveryWithoutChangingCardData() {
        val original = BankCard.demos().first().copy(appLink = AppLink("test.bank", detectedName = "银行"))
        val (removed, entry) = VaultSnapshot(listOf(original)).remove(original.id, 1000L)!!
        assertEquals(original, removed.restore(entry.id, 2000L).cards.single())
    }
    @Test fun disabledHapticsSuppressEveryFeedbackScenario() {
        var enabled = true
        val calls = mutableListOf<TouchEvent>()
        val feedback = WalletHaptics({ enabled }, calls::add)
        feedback.perform(TouchEvent.Copied)
        enabled = false
        TouchEvent.entries.forEach(feedback::perform)
        assertEquals(listOf(TouchEvent.Copied), calls)
        enabled = true
        feedback.perform(TouchEvent.SortSettled)
        assertEquals(2, calls.size)
    }
}
