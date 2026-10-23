package com.dieka.wallet

import com.dieka.wallet.data.CropRegion
import org.junit.Assert.*
import org.junit.Test

class CropGeometryTest {
    @Test fun rotatedPreviewSelectionMapsBackToOriginalCorners() {
        val topLeft = CropRegion(0f, 0f, .5f, .5f)
        assertEquals(topLeft, topLeft.unrotate(0))
        assertEquals(CropRegion(0f, .5f, .5f, 1f), topLeft.unrotate(1))
        assertEquals(CropRegion(.5f, .5f, 1f, 1f), topLeft.unrotate(2))
        assertEquals(CropRegion(.5f, 0f, 1f, .5f), topLeft.unrotate(3))
        assertEquals(topLeft, topLeft.unrotate(4))
    }
}
