package com.dieka.wallet.data

/** Coordinates in the oriented image, independent of preview size and screen density. */
data class CropRegion(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    fun unrotate(turns: Int): CropRegion = when (turns.mod(4)) {
        1 -> CropRegion(top, 1 - right, bottom, 1 - left)
        2 -> CropRegion(1 - right, 1 - bottom, 1 - left, 1 - top)
        3 -> CropRegion(1 - bottom, left, 1 - top, right)
        else -> this
    }
}
