package com.dieka.wallet

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.geometry.Offset
import android.graphics.Bitmap
import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import android.content.ClipboardManager
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Runs against a fresh install; all values below are public test card data. */
class WalletUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun demoCardCanOpenRevealAndHide() {
        compose.onNodeWithText("我的卡包").assertIsDisplayed()
        takeDemoScreenshot("preview-home.png")
        compose.onNodeWithTag("card-demo-1").performTouchInput { click(Offset(center.x, 30f)) }
        compose.onNodeWithText("卡片信息").performClick()
        compose.onNodeWithText("显示其他信息").performClick()
        takeDemoScreenshot("preview-details.png")
        compose.onNodeWithText("4111 1111 1111 1111").assertIsDisplayed()
        compose.onNodeWithText("12/29").assertIsDisplayed()
        compose.onNodeWithText("123", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("隐藏其他信息").performClick()
        compose.onNodeWithText("4111 1111 1111 1111").assertIsDisplayed()
        compose.onNodeWithText("12/29").assertDoesNotExist()
        compose.onNodeWithTag("card-demo-1").performClick()
        compose.onNodeWithTag("card-information").assertDoesNotExist()
        compose.onNodeWithText("我的卡包").assertIsDisplayed()
    }

    private fun takeDemoScreenshot(name: String) {
        compose.activityRule.scenario.onActivity {
            assertEquals(0, it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
        }
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            WindowInspector.getGlobalWindowViews().forEach { view ->
                val params = view.layoutParams as? WindowManager.LayoutParams ?: return@forEach
                assertEquals(0, params.flags and WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        Thread.sleep(250)
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.filesDir, name).outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
    }

    @Test fun searchAndFilterCanFindDebitCard() {
        compose.onNodeWithText("储蓄卡", substring = false).performClick()
        compose.onNodeWithContentDescription("搜索卡片").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("0077")
        compose.onNodeWithText("林间来信 · 一卡通").assertIsDisplayed()
        compose.onNodeWithText("自由人生 · 经典白").assertDoesNotExist()
        compose.onNodeWithContentDescription("展开全部卡片").performClick()
        compose.onNodeWithContentDescription("堆叠卡片").assertExists()
    }

    @Test fun backgroundingRetainsFocusAndNumberButHidesOtherFields() {
        compose.onNodeWithTag("card-demo-1").performTouchInput { click(Offset(center.x, 30f)) }
        compose.onNodeWithText("卡片信息").performClick()
        compose.onNodeWithText("显示其他信息").performClick()
        compose.onNodeWithText("4111 1111 1111 1111").assertIsDisplayed()
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.onNodeWithText("4111 1111 1111 1111").assertIsDisplayed()
        compose.onNodeWithText("显示其他信息").assertIsDisplayed()
        compose.onNodeWithText("12/29").assertDoesNotExist()
        compose.onNodeWithText("我的卡包").assertIsDisplayed()
    }

    @Test fun homeUsesSpaceForCardsInsteadOfPromotionalFooter() {
        compose.onNodeWithText("每一张，都值得好好收藏。").assertDoesNotExist()
        compose.onNodeWithText("轻点卡片查看详情 · 上下滑动浏览").assertDoesNotExist()
        compose.onNodeWithText("仅存于此设备 · 本地加密").assertDoesNotExist()
        compose.onNodeWithText("叠卡").assertDoesNotExist()
        compose.onNodeWithTag("card-demo-6").assertIsDisplayed()
        val toggle = compose.onNodeWithTag("layout-toggle").fetchSemanticsNode().boundsInRoot
        val cards = compose.onNodeWithTag("card-list").fetchSemanticsNode().boundsInRoot
        assertTrue(toggle.center.x > cards.center.x && toggle.center.y > cards.center.y)
    }

    @Test fun dragSortSavesOrderAndReopeningRetainsIt() {
        compose.onNodeWithContentDescription("更多选项").performClick()
        compose.onNodeWithText("自定义排序").performClick()
        val rowHeight = compose.onNodeWithTag("sort-demo-1").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithTag("drag-demo-1").performTouchInput {
            swipe(center, center + Offset(0f, rowHeight * 1.4f), durationMillis = 700)
        }
        takeDemoScreenshot("preview-sort.png")
        compose.onNodeWithText("完成", substring = false).performClick()
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity {
            val model = ViewModelProvider(it)[WalletViewModel::class.java]
            assertEquals(listOf("demo-2", "demo-1", "demo-3", "demo-4", "demo-5", "demo-6"), model.cards.map { card -> card.id })
            assertTrue(model.demo)
        }
        compose.onNodeWithContentDescription("更多选项").performClick()
        compose.onNodeWithText("自定义排序").performClick()
        val first = compose.onNodeWithTag("sort-demo-2").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("sort-demo-1").fetchSemanticsNode().boundsInRoot
        assertTrue(first.top < second.top)
        compose.onNodeWithContentDescription("取消排序").performClick()
        compose.onNodeWithText("卡片排序", substring = false).assertDoesNotExist()
    }

    @Test fun cancellingSortDiscardsDraftAndAccessibleMoveWorks() {
        compose.onNodeWithText("储蓄卡", substring = false).performClick()
        compose.onNodeWithContentDescription("更多选项").performClick()
        compose.onNodeWithText("自定义排序").performClick()
        // Sorting always includes all cards, even when the home list is filtered.
        compose.onNodeWithTag("sort-demo-1").assertIsDisplayed()
        val actions = compose.onNodeWithTag("sort-demo-1").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle {
            assertTrue(actions.first { it.label == "下移" }.action())
        }
        val first = compose.onNodeWithTag("sort-demo-2").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("sort-demo-1").fetchSemanticsNode().boundsInRoot
        assertTrue(first.top < second.top)
        compose.onNodeWithContentDescription("取消排序").performClick()
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity {
            assertEquals("demo-1", ViewModelProvider(it)[WalletViewModel::class.java].cards.first().id)
        }
    }

    @Test fun rapidLayoutTogglesAndSearchTransitionsRemainUsable() {
        compose.mainClock.autoAdvance = false
        repeat(4) {
            compose.onNodeWithTag("layout-toggle").performClick()
            compose.mainClock.advanceTimeBy(80)
        }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithContentDescription("展开全部卡片").assertExists()
        repeat(2) { compose.onNodeWithContentDescription("搜索卡片").performClick() }
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithTag("card-demo-1").performTouchInput { click(Offset(center.x, 30f)) }
        compose.onNodeWithTag("card-demo-1").performClick()
        compose.onNodeWithTag("card-demo-1").performTouchInput { click(Offset(center.x, 30f)) }
        compose.onNodeWithText("卡片信息").assertIsDisplayed()
    }

    @Test fun swipesSwitchOneFilteredCardAndNeverTriggerCollapse() {
        compose.onNodeWithText("储蓄卡", substring = false).performClick()
        compose.onNodeWithTag("card-demo-5").performTouchInput { click(Offset(center.x, 30f)) }
        compose.onNodeWithTag("card-demo-5").performTouchInput { swipe(center, center + Offset(0f, 100f), 700) }
        compose.onNodeWithTag("card-demo-5").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已展开"))
        compose.onNodeWithTag("card-demo-5").performTouchInput { swipe(Offset(center.x, height * .85f), Offset(center.x, height * .1f), 400) }
        compose.onNodeWithTag("card-demo-6").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已展开"))
        compose.onNodeWithTag("card-demo-6").performTouchInput { swipe(Offset(center.x, height * .8f), Offset(center.x, height * .1f), 400) }
        compose.onNodeWithTag("card-demo-6").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已展开"))
        compose.onNodeWithTag("card-demo-6").performTouchInput { swipe(Offset(center.x, height * .1f), Offset(center.x, height * .85f), 400) }
        compose.onNodeWithTag("card-demo-5").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已展开"))
        // Exceed Android's touch slop, while remaining below the card-switch distance.
        compose.onNodeWithTag("card-demo-5").performTouchInput { swipe(center, center + Offset(0f, -80f), 700) }
        compose.onNodeWithTag("card-demo-5").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已展开"))
    }

    @Test fun copyNumberAndThirtySecondTimerLeaveNumberVisible() {
        compose.onNodeWithTag("card-demo-1").performTouchInput { click(Offset(center.x, 30f)) }
        compose.onNodeWithText("卡片信息").performClick()
        compose.onNodeWithContentDescription("复制卡号").performClick()
        compose.runOnIdle {
            val clipboard = compose.activity.getSystemService(ClipboardManager::class.java)
            assertEquals("4111111111111111", clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
        compose.onNodeWithText("显示其他信息").performClick()
        compose.mainClock.advanceTimeBy(30_100)
        compose.onNodeWithText("显示其他信息").assertIsDisplayed()
        compose.onNodeWithText("4111 1111 1111 1111").assertIsDisplayed()
    }

    @Test fun scrollingInformationDoesNotSwitchCardsAndNextCardStartsHidden() {
        compose.onNodeWithTag("card-demo-1").performTouchInput { click(Offset(center.x, 30f)) }
        compose.onNodeWithText("卡片信息").performClick()
        compose.onNodeWithText("显示其他信息").performClick()
        compose.onNodeWithTag("card-information").performTouchInput { swipeUp() }
        compose.runOnIdle {
            val model = ViewModelProvider(compose.activity)[WalletViewModel::class.java]
            assertEquals("demo-1", model.selectedId)
            assertTrue(model.infoOpen)
        }
        compose.onNodeWithTag("card-list").performScrollToIndex(0)
        compose.onNodeWithTag("card-demo-1").performTouchInput { swipeUp() }
        compose.onNodeWithTag("card-demo-2").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已展开"))
        compose.onNodeWithTag("card-information").assertDoesNotExist()
        compose.runOnIdle {
            val model = ViewModelProvider(compose.activity)[WalletViewModel::class.java]
            assertTrue(!model.revealed && !model.infoOpen)
        }
    }

    @Test fun threeDensityOptionsScaleWholeCardsAndPersistPreference() {
        compose.onNodeWithContentDescription("展开全部卡片").performClick()
        var previous = Float.MAX_VALUE
        for (count in 3..5) {
            compose.onNodeWithContentDescription("更多选项").performClick()
            compose.onNodeWithText("卡片大小").performClick()
            compose.onNodeWithTag("density-$count").performClick()
            val bounds = compose.onNodeWithTag("card-demo-1").fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.height < previous)
            assertEquals(1.586f, bounds.width / bounds.height, .02f)
            previous = bounds.height
            takeDemoScreenshot("preview-density-$count.png")
            assertEquals(count, compose.activity.getSharedPreferences("display", 0).getInt("compact_count", -1))
        }
        compose.runOnIdle { ViewModelProvider(compose.activity)[WalletViewModel::class.java].updateCompactCount(4) }
    }
}
