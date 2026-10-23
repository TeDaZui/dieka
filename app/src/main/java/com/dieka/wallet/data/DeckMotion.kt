package com.dieka.wallet.data

import kotlin.math.abs

object DeckMotion {
    fun expansion(index: Int, origin: Int, shift: Float): Float = (1f - abs(index - origin - shift)).coerceIn(0f, 1f)
    fun target(shift: Float, velocity: Float, previous: Boolean, next: Boolean): Int {
        val direction = when {
            velocity > 1f -> 1
            velocity < -1f -> -1
            shift >= .28f -> 1
            shift <= -.28f -> -1
            else -> 0
        }
        return if ((direction < 0 && !previous) || (direction > 0 && !next)) 0 else direction
    }
}
