package com.dieka.wallet

import com.dieka.wallet.data.DeckMotion
import org.junit.Assert.*
import org.junit.Test

class DeckMotionTest {
    @Test fun outgoingAndIncomingExpansionFollowTheSameContinuousPosition() {
        for (n in 0..100) {
            val p = n / 100f
            assertEquals(1f - p, DeckMotion.expansion(3, 3, p), .00001f)
            assertEquals(p, DeckMotion.expansion(4, 3, p), .00001f)
            assertEquals(0f, DeckMotion.expansion(5, 3, p), .00001f)
            assertEquals(p, DeckMotion.expansion(2, 3, -p), .00001f)
        }
    }
    @Test fun releaseUsesDistanceOrVelocityButNeverWrapsOrSkipsCards() {
        assertEquals(0, DeckMotion.target(.15f, .1f, true, true))
        assertEquals(1, DeckMotion.target(.4f, 0f, true, true))
        assertEquals(-1, DeckMotion.target(-.4f, 0f, true, true))
        assertEquals(1, DeckMotion.target(.1f, 2f, true, true))
        assertEquals(0, DeckMotion.target(-.7f, -4f, false, true))
        assertEquals(0, DeckMotion.target(.7f, 4f, true, false))
        assertEquals(-1, DeckMotion.target(.2f, -3f, true, true))
    }
}
