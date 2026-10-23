package com.dieka.wallet

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.test.platform.app.InstrumentationRegistry
import com.dieka.wallet.data.CropRegion
import com.dieka.wallet.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class CropDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun source(name: String, width: Int = 1600, height: Int = 1000, exif: Boolean = false): Uri {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.BLUE)
            drawRect(0f, 0f, width / 2f, height.toFloat(), Paint().apply { color = Color.RED })
        }
        val file = File(context.cacheDir, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        if (exif) ExifInterface(file.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString()); saveAttributes()
        }
        return Uri.fromFile(file)
    }
    private fun bitmap(encoded: String): Bitmap {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
    private fun assertRed(bitmap: Bitmap) {
        val color = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        assertTrue(Color.red(color) > 220 && Color.blue(color) < 30)
    }

    @Test fun croppingUsesOriginalCoordinatesAndPreservesFreeAspect() {
        val uri = source("crop-landscape.jpg", 4000, 2400)
        bitmap(renderCrop(context, uri, CropRegion(0f, 0f, .4f, 1f), 0)).useBitmap {
            assertTrue(width <= 1200 && height <= 1200)
            assertEquals(2f / 3f, width.toFloat() / height, .003f)
            assertRed(this)
        }
        bitmap(renderCrop(context, uri, CropRegion(0f, 0f, .4f, 1f), 1)).useBitmap {
            assertEquals(1.5f, width.toFloat() / height, .003f)
            assertRed(this)
        }
    }

    @Test fun exifOrientationIsAppliedBeforeCropCoordinates() {
        val uri = source("crop-oriented.jpg", exif = true)
        bitmap(renderCrop(context, uri, CropRegion(0f, 0f, 1f, .4f), 0)).useBitmap {
            assertEquals(1000, width)
            assertEquals(640, height)
            assertRed(this)
        }
    }

    @Test fun viewportSupportsCornerResizePinchPanAndReset() {
        val state = CropViewport()
        state.initialize(IntSize(600, 800), IntSize(1600, 1000), false)
        val original = state.crop
        assertEquals(1.586f, original.width / original.height, .001f)
        state.resize(2, Offset(-50f, -10f), false)
        assertTrue(state.crop.width < original.width)
        state.resize(2, Offset(-60f, 0f), true)
        assertTrue(kotlin.math.abs(state.crop.width / state.crop.height - 1.586f) > .1f)
        val region = state.region()
        state.transform(2f, Offset(-10f, -10f), state.crop.center)
        assertTrue(state.region().width < region.width)
        state.transform(1f, Offset(10000f, 10000f), state.crop.center)
        assertTrue(state.region().left >= 0 && state.region().right <= 1)
        state.reset(false)
        assertEquals(original, state.crop)
    }

    @Test fun cropPreviewCanRotateResetAndConfirm() {
        var result: String? = null
        compose.setContent { DiekaTheme { CardCropper(sourceUri, {}, { result = it }) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("crop-canvas").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("自由比例").performClick()
        compose.onNodeWithContentDescription("旋转图片").performClick()
        compose.onNodeWithText("重置").performClick()
        compose.onNodeWithText("银行卡比例").assertIsDisplayed()
        screenshot("preview-crop.png")
        compose.onNodeWithText("使用图片").performClick()
        compose.waitUntil(10_000) { result != null }
        bitmap(result!!).useBitmap { assertEquals(1.586f, width.toFloat() / height, .01f) }
    }
    private val sourceUri by lazy { source("crop-ui.jpg") }

    @Test fun cancelDoesNotCommitImage() {
        var cancelled = false
        var committed = false
        compose.setContent { DiekaTheme { CardCropper(sourceUri, { cancelled = true }, { committed = true }) } }
        compose.onNodeWithContentDescription("取消裁剪").performClick()
        compose.waitUntil { cancelled }
        assertFalse(committed)
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.filesDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun Bitmap.useBitmap(action: Bitmap.() -> Unit) { try { action() } finally { recycle() } }
}
