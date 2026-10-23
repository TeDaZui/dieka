package com.dieka.wallet.data

/** Monotonic timestamps only. No persisted authentication or bypass across process restarts. */
class SessionPolicy {
    private var verifiedAt: Long? = null
    private var backgroundAt: Long? = null

    fun verified(now: Long) { verifiedAt = now }
    fun canReuse(now: Long): Boolean = verifiedAt?.let { now - it in 0 until REUSE_MS } ?: false
    fun background(now: Long) { backgroundAt = now }
    fun mustLock(now: Long): Boolean = backgroundAt?.let { now - it >= BACKGROUND_MS } ?: false
    fun foreground() { backgroundAt = null }
    fun clear() { verifiedAt = null; backgroundAt = null }

    companion object {
        const val BACKGROUND_MS = 300_000L
        // Small margin before the existing Keystore key's 300-second authentication deadline.
        const val REUSE_MS = 295_000L
    }
}
