package com.dieka.wallet

import android.os.Build
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.dieka.wallet.data.BankCard
import com.dieka.wallet.data.VaultStore
import com.dieka.wallet.data.*
import android.graphics.Bitmap
import java.io.File
import java.security.KeyStore
import javax.crypto.SecretKey
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in on a fresh, isolated emulator only; never operates on an existing personal vault. */
class VaultOrderDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun authenticatedSortPersistsAcrossReloadAndLaterEdits() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("isolatedSortPersistence") == "true" && Build.HARDWARE == "ranchu")
        val pin = args.getString("testPin").orEmpty()
        require(pin.matches(Regex("[0-9]{4,8}")))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var model: WalletViewModel
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[WalletViewModel::class.java] }
        check(model.demo && !VaultStore(instrumentation.targetContext).exists())
        val cards = BankCard.demos().take(3).map { it.copy(id = "persist-${it.id}") }.toMutableList()
        cards[0] = cards[0].copy(appLink = AppLink("com.android.settings", detectedName = "Settings", customName = "设置"))

        compose.onNodeWithContentDescription("添加银行卡").performClick()
        compose.waitUntil(10_000) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            root?.packageName?.toString() == "com.android.systemui" &&
                root.findAccessibilityNodeInfosByViewId("com.android.systemui:id/lockPassword").any { it.isFocused }
        }
        instrumentation.uiAutomation.executeShellCommand("input text $pin").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        instrumentation.uiAutomation.executeShellCommand("input keyevent 66").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        compose.waitUntil(10_000) { model.showEditor && !model.busy }
        cards.forEach { card ->
            compose.runOnIdle { model.save(card) }
            compose.waitUntil(10_000) { !model.busy }
        }
        assertTrue(model.canReuseAuthentication())
        compose.onNodeWithContentDescription("更多选项").performClick()
        compose.onNodeWithText("自定义排序").performClick()
        val moves = compose.onNodeWithTag("sort-${cards[0].id}").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { moves.first { it.label == "下移" }.action() }
        compose.onNodeWithText("完成", substring = false).performClick()
        compose.waitUntil(10_000) { !model.busy && !model.showSort }
        val expected = listOf(cards[1].id, cards[0].id, cards[2].id)
        assertEquals(expected, VaultStore(instrumentation.targetContext).load().cards.map { it.id })
        compose.runOnIdle { model.save(cards[0].copy(title = "Changed after sorting")) }
        compose.waitUntil(10_000) { !model.busy }
        val loaded = VaultStore(instrumentation.targetContext).load().cards
        assertEquals(expected, loaded.map { it.id })
        assertEquals("Changed after sorting", loaded[1].title)
        assertEquals(cards[0].appLink, loaded[1].appLink)
        compose.onNodeWithTag("card-more").performClick()
        compose.onNodeWithText("编辑卡片", substring = false).performClick()
        compose.onNodeWithContentDescription("取消编辑").assertIsDisplayed()
        compose.onNodeWithText("知道了", substring = false).assertDoesNotExist()
        compose.onNodeWithContentDescription("取消编辑").performClick()
        compose.runOnIdle { assertEquals(cards[0].id, model.selectedId) }
        compose.runOnIdle { model.lock(); model.authenticated(); model.unlock() }
        compose.waitUntil(10_000) { !model.busy && !model.locked }
        assertEquals(expected, model.cards.map { it.id })

        // Exercise removal through the actual UI, then the latest-operation undo action.
        compose.runOnIdle { model.notice = null; model.focus(model.cards[1]) }
        compose.onNodeWithTag("card-more").performClick()
        compose.onNodeWithText("移除", substring = false).performClick()
        compose.waitUntil(10_000) { !model.busy && model.trash.size == 1 }
        compose.onNodeWithText("已移除", substring = false).assertIsDisplayed()
        compose.onNodeWithText("撤销", substring = false).performClick()
        compose.waitUntil(10_000) { !model.busy && model.trash.isEmpty() }
        assertEquals(expected, model.cards.map { it.id })
        compose.runOnIdle { model.delete(model.cards[0]) }
        compose.waitUntil(10_000) { !model.busy }
        val firstRemoved = model.trash.single()
        compose.runOnIdle { model.delete(model.cards[0]) }
        compose.waitUntil(10_000) { !model.busy }
        val lastRemoved = model.trash.first()
        assertEquals(lastRemoved.id, model.notice?.undoId)
        compose.runOnIdle { model.restore(lastRemoved.id) }
        compose.waitUntil(10_000) { !model.busy }
        assertEquals(listOf(firstRemoved.id), model.trash.map { it.id })

        // Sorting, editing and locked reload must preserve the other half of the snapshot.
        compose.runOnIdle { model.save(model.cards.first().copy(holder = "TEST HOLDER")) }
        compose.waitUntil(10_000) { !model.busy }
        assertEquals(firstRemoved, VaultStore(instrumentation.targetContext).load().trash.single())
        compose.runOnIdle { model.lock(); model.authenticated(); model.unlock() }
        compose.waitUntil(10_000) { !model.busy && !model.locked }
        compose.runOnIdle { model.notice = null; model.showTrash = true }
        compose.onNodeWithTag("trash-${firstRemoved.card.id}").assertIsDisplayed()
        compose.waitForIdle()
        val image = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.filesDir, "preview-trash.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        image.recycle()
        compose.onNodeWithText("恢复", substring = false).performClick()
        compose.waitUntil(10_000) { !model.busy && model.trash.isEmpty() }
        compose.runOnIdle { model.notice = null; model.showTrash = false }
        assertEquals(expected, model.cards.map { it.id })

        val store = VaultStore(instrumentation.targetContext)
        val file = File(instrumentation.targetContext.noBackupFilesDir, "cards.vault")
        val key = KeyStore.getInstance("AndroidKeyStore").run { load(null); getKey("dieka.vault.aes.v1", null) as SecretKey }
        val legacy = JSONArray().apply { store.load().cards.forEach { put(CardCodec.encode(it)) } }.toString()
        val legacyBytes = VaultCipher.encrypt(legacy.toByteArray(), key)
        file.writeBytes(legacyBytes)
        assertEquals(expected, store.load().cards.map { it.id })
        assertArrayEquals(legacyBytes, file.readBytes()) // Reading alone must not rewrite the old file.

        // Force AtomicFile's new-file creation to fail; the legacy file and visible cards survive.
        val obstruction = File(file.path + ".new")
        check(obstruction.mkdir())
        try {
            compose.runOnIdle { model.delete(model.cards.first()) }
            compose.waitUntil(10_000) { !model.busy }
            assertEquals(expected, model.cards.map { it.id })
            assertTrue(model.trash.isEmpty())
            assertTrue(model.notice!!.text.startsWith("保存失败"))
            assertNull(model.notice!!.undoId)
            assertArrayEquals(legacyBytes, file.readBytes())
        } finally { check(obstruction.delete()) }

        // Successful next write migrates the array while retaining the cipher/key and image bytes.
        compose.runOnIdle { model.authenticated(); model.notice = null; model.delete(model.cards.first()) }
        compose.waitUntil(10_000) { !model.busy }
        val decoded = JSONObject(String(VaultCipher.decrypt(file.readBytes(), key)))
        assertEquals(2, decoded.getInt("version"))
        assertEquals(1, decoded.getJSONArray("trash").length())
        val saved = store.load()
        val expired = saved.trash.single().copy(deletedAt = System.currentTimeMillis() - TrashEntry.RETENTION_MS)
        store.save(saved.copy(trash = listOf(expired)))
        compose.runOnIdle { model.lock(); model.authenticated(); model.unlock() }
        compose.waitUntil(10_000) { !model.busy && !model.locked }
        assertTrue(model.trash.isEmpty())
        assertTrue(store.load().trash.isEmpty())
    }
}
