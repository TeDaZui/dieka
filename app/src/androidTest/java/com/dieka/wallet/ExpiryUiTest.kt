package com.dieka.wallet

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.dieka.wallet.data.*
import com.dieka.wallet.ui.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ExpiryUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun multipleExpiryBannerKeepsSuffixVisibleAndDisappearsWithoutSpace() {
        val cards = BankCard.demos()
        var entries by mutableStateOf(listOf(ExpiringCard(cards[0].copy(bank = "这是一个非常长的银行名称用于验证省略"), 7), ExpiringCard(cards[1], 7)))
        var clicked = false
        compose.setContent {
            DiekaTheme {
                Surface(color = Paper) {
                    Column(Modifier.fillMaxSize().padding(8.dp)) {
                        Text("我的卡包", Modifier.padding(12.dp))
                        Row { listOf("全部", "信用卡", "储蓄卡").forEach { FilterChip(it == "全部", {}, { Text(it) }) } }
                        ExpiryBanner(entries) { clicked = true }
                        CardBrowser(cards, false, 4, null, false, false, true, false, {}, {}, {}, {}, {}, Modifier.weight(1f))
                    }
                }
            }
        }
        compose.onNodeWithText(" · 尾号 1111 · 7 天后到期", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("  另有 1 张", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("expiry-banner").performClick()
        assertTrue(clicked)
        compose.runOnIdle { entries = listOf(ExpiringCard(cards[0], 1), ExpiringCard(cards[1], 1)) }
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.filesDir, "preview-expiry.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
        val before = compose.onNodeWithTag("card-list").fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle { entries = emptyList() }
        compose.onNodeWithTag("expiry-banner").assertDoesNotExist()
        assertTrue(compose.onNodeWithTag("card-list").fetchSemanticsNode().boundsInRoot.top < before)
    }
}
