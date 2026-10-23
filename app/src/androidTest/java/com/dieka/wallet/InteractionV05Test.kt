package com.dieka.wallet

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.dieka.wallet.data.InstalledApps
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class InteractionV05Test {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val model get() = ViewModelProvider(compose.activity)[WalletViewModel::class.java]

    @Test fun middleCardExpandsInPlaceAndRowsMoveContinuouslyDuringSwipe() {
        val top = compose.onNodeWithTag("row-demo-3").fetchSemanticsNode().boundsInRoot.top
        val below = compose.onNodeWithTag("row-demo-4").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("card-demo-3").performTouchInput { click(Offset(center.x, 30f)) }
        val expanded = compose.onNodeWithTag("row-demo-3").fetchSemanticsNode().boundsInRoot
        assertEquals(top, expanded.top, 2f)
        val next = compose.onNodeWithTag("row-demo-4").fetchSemanticsNode().boundsInRoot
        assertTrue(next.top > below + 250f)
        screenshot("preview-in-place.png")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("card-demo-3").performTouchInput {
            down(Offset(center.x, height * .7f)); moveBy(Offset(0f, -height * .4f), 180)
        }
        compose.mainClock.advanceTimeBy(48)
        compose.waitForIdle()
        val partialOld = compose.onNodeWithTag("row-demo-3").fetchSemanticsNode().boundsInRoot
        val partialNext = compose.onNodeWithTag("row-demo-4").fetchSemanticsNode().boundsInRoot
        assertTrue("Outgoing row must already shrink under the finger", partialOld.height < expanded.height - 50f)
        assertTrue("Incoming row must already expand before release", partialNext.height > next.height + 50f)
        assertTrue("Following rows move during the gesture", partialNext.top < next.top - 50f)
        assertEquals("demo-3", model.selectedId)
        screenshot("preview-mid-swipe.png")
        compose.onNodeWithTag("card-demo-3").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("card-demo-4").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已展开"))
        val landed = compose.onNodeWithTag("row-demo-4").fetchSemanticsNode().boundsInRoot
        assertEquals("New card lands at the same browsing position", top, landed.top, 5f)
        compose.onNodeWithTag("card-demo-4").performClick()
        compose.onNodeWithTag("card-info").assertDoesNotExist()
    }

    @Test fun overflowIsAnchoredAndHistoryContainsEveryRelease() {
        compose.onNodeWithTag("card-demo-2").performTouchInput { click(Offset(center.x, 30f)) }
        compose.onNodeWithText("编辑卡片").assertDoesNotExist()
        compose.onNodeWithTag("card-more").performClick()
        compose.onNodeWithText("编辑卡片").assertIsDisplayed()
        compose.onNodeWithText("移除", substring = false).assertIsDisplayed()
        screenshot("preview-card-menu.png")
        shell("input keyevent 4")
        compose.onNodeWithContentDescription("更多选项").performClick()
        compose.onNodeWithText("更新日志").performClick()
        compose.onNodeWithText("v0.5.0 · 当前版本").assertIsDisplayed()
        screenshot("preview-changelog.png")
        for (version in listOf("0.4.0", "0.3.0", "0.2.0", "0.1.0")) {
            compose.onNodeWithTag("release-notes").performScrollToNode(hasText("v$version"))
            compose.onNodeWithText("v$version").assertIsDisplayed()
        }
    }

    @Test fun choosingCustomNameLaunchingAndReturningPreservesTheScene() {
        compose.onNodeWithTag("card-demo-3").performTouchInput { click(Offset(center.x, 30f)) }
        compose.onNodeWithText("卡片信息").performClick()
        val before = compose.onNodeWithTag("row-demo-3").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("card-app").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("app-list").fetchSemanticsNodes().isNotEmpty() }
        val app = InstalledApps.list(compose.activity).first { it.packageName == "com.android.settings" }
        compose.onNodeWithTag("app-search").performTextInput(app.detectedName)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("app-com.android.settings").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("app-com.android.settings").performClick()
        compose.onNodeWithTag("app-custom-name").performTextInput("测试银行")
        screenshot("preview-app-link.png")
        compose.onNodeWithText("保存关联").performClick()
        compose.onNodeWithText("打开测试银行").assertIsDisplayed()
        assertEquals(before, compose.onNodeWithTag("row-demo-3").fetchSemanticsNode().boundsInRoot.top, 2f)
        compose.onNodeWithTag("card-app").performClick()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        compose.waitUntil(10_000) { automation.rootInActiveWindow?.packageName?.toString() == "com.android.settings" }
        shell("input keyevent 4")
        val targetPackage = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        compose.waitUntil(10_000) { automation.rootInActiveWindow?.packageName?.toString() == targetPackage }
        compose.onNodeWithText("打开测试银行").assertIsDisplayed()
        assertEquals("demo-3", model.selectedId)
        assertTrue(model.infoOpen)
        assertEquals(before, compose.onNodeWithTag("row-demo-3").fetchSemanticsNode().boundsInRoot.top, 2f)
        compose.onNodeWithTag("card-app").performTouchInput { longClick() }
        compose.onNodeWithTag("app-custom-name").performTextClearance()
        compose.onNodeWithText("保存关联").performClick()
        compose.runOnIdle { assertEquals(model.detail!!.appLink!!.detectedName, model.detail!!.appLink!!.displayName) }
        compose.onNodeWithTag("card-app").performTouchInput { longClick() }
        compose.onNodeWithText("解除关联").performClick()
        compose.onNodeWithTag("card-app").assertTextContains("关联应用")
    }

    @Test fun hapticPreferenceIsPersistentAndCanBeReenabled() {
        compose.onNodeWithContentDescription("更多选项").performClick()
        val enabled = model.hapticsEnabled
        compose.onNodeWithTag("haptics-toggle").performClick()
        compose.runOnIdle {
            assertEquals(!enabled, model.hapticsEnabled)
            assertEquals(!enabled, compose.activity.getSharedPreferences("display", 0).getBoolean("haptics", enabled))
        }
        compose.onNodeWithTag("haptics-toggle").performClick()
        compose.runOnIdle { assertEquals(enabled, model.hapticsEnabled) }
    }

    @Test fun activityRecreationRetainsSearchCategoryLayoutAndSelectedCard() {
        compose.onNodeWithText("储蓄卡", substring = false).performClick()
        compose.onNodeWithContentDescription("搜索卡片").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("0077")
        compose.onNodeWithTag("layout-toggle").performClick()
        compose.onNodeWithTag("card-demo-6").performClick()
        compose.onNodeWithText("卡片信息").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNode(hasSetTextAction()).assertTextContains("0077")
        compose.onNodeWithContentDescription("堆叠卡片").assertExists()
        compose.onNodeWithTag("card-demo-6").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已展开"))
        compose.onNodeWithTag("card-information").assertExists()
        compose.runOnIdle { assertEquals(2, model.filterState.intValue) }
    }

    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle(); Thread.sleep(300)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.filesDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
