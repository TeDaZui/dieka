package com.dieka.wallet

import com.dieka.wallet.data.SessionPolicy
import org.junit.Assert.*
import org.junit.Test

class SessionPolicyTest {
    @Test fun verificationIsReusedUntilKeyDeadline() {
        val session = SessionPolicy()
        assertFalse(session.canReuse(1000))
        session.verified(1000)
        assertTrue(session.canReuse(1000))
        assertTrue(session.canReuse(295999))
        assertFalse(session.canReuse(296000))
    }
    @Test fun shortBackgroundKeepsSessionAndLongBackgroundLocks() {
        val session = SessionPolicy()
        session.verified(1000)
        session.background(2000)
        assertFalse(session.mustLock(301999))
        assertTrue(session.mustLock(302000))
        session.foreground()
        assertFalse(session.mustLock(400000))
    }
    @Test fun manualLockAndNewProcessRequireVerification() {
        val session = SessionPolicy()
        session.verified(1000)
        session.clear()
        assertFalse(session.canReuse(1001))
        assertFalse(SessionPolicy().canReuse(1001))
    }
}
