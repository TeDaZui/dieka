package com.dieka.wallet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dieka.wallet.data.BankCard
import com.dieka.wallet.ui.CardSort
import com.dieka.wallet.ui.DiekaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SortUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun draggingAtBottomScrollsPastInitiallyVisibleCardsWithoutLosingRecords() {
        val cards = (1..24).map { BankCard(id = "long-$it", bank = "Bank $it", title = "Card $it", number = "4111111111111111") }
        var saved: List<String>? = null
        compose.setContent { DiekaTheme { CardSort(cards, false, true, {}, {}, { saved = it }) } }
        val handle = compose.onNodeWithTag("drag-long-1")
        val handleBounds = handle.fetchSemanticsNode().boundsInRoot
        val listBounds = compose.onNodeWithTag("sort-list").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        handle.performTouchInput {
            down(center)
            moveTo(Offset(center.x, listBounds.bottom - handleBounds.top - 20f), delayMillis = 500)
        }
        // Let Android measure the LazyColumn between frames as new rows enter the viewport.
        repeat(80) {
            compose.mainClock.advanceTimeBy(32)
            compose.waitForIdle()
        }
        handle.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText("完成", substring = false).performClick()
        compose.runOnIdle {
            assertNotNull(saved)
            assertEquals(cards.map { it.id }.toSet(), saved!!.toSet())
            assertEquals(24, saved!!.size)
            assertTrue("Dragging at the edge should reach beyond the initial viewport: $saved", saved!!.indexOf("long-1") >= 8)
        }
    }
}
