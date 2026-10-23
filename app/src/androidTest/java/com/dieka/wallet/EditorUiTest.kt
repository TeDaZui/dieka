package com.dieka.wallet

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dieka.wallet.data.BankCard
import com.dieka.wallet.data.CardDesign
import com.dieka.wallet.ui.CardEditor
import com.dieka.wallet.ui.DiekaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EditorUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun typedExpiryAndCardDataAreSavedWithoutReorderingDigits() {
        var saved: BankCard? = null
        compose.setContent {
            DiekaTheme {
                CardEditor(null, false, {}, { saved = it }, {})
            }
        }
        fun input(label: String, value: String) {
            compose.onNode(hasSetTextAction() and hasText(label)).performScrollTo().performTextInput(value)
        }
        input("发卡银行", "Test Bank")
        input("卡片名称", "Daily")
        input("银行卡号", "4111111111111111")
        // Separate keystrokes reproduce the cursor issue caused by inserting '/' into model text.
        listOf("1", "2", "2", "9").forEach { input("有效期", it) }
        input("安全码（可选）", "123")
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle {
            assertEquals("12/29", saved?.expiry)
            assertEquals("4111111111111111", saved?.number)
            assertEquals("123", saved?.cvv)
            assertEquals(CardDesign(), saved?.design)
        }
    }

    @Test fun editFaceInPlaceThenChangeAccountFieldsKeepsBothIndependent() {
        var saved: BankCard? = null
        val initial = BankCard(bank = "Account Bank", title = "Account Title", number = "4111111111111111", expiry = "12/29")
        compose.setContent { DiekaTheme { CardEditor(initial, false, {}, { saved = it }, {}) } }
        compose.onNodeWithTag("design-BANK").performClick()
        compose.onNode(hasSetTextAction() and hasText("卡面文字")).performTextInput("Face Bank")
        compose.onNodeWithText("完成").performClick()
        compose.onNode(hasSetTextAction() and hasText("发卡银行")).performScrollTo().performTextReplacement("Different Bank")
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle {
            assertEquals("Different Bank", saved?.bank)
            assertEquals("Face Bank", saved?.design?.bank)
            assertEquals("", saved?.design?.number)
        }
    }
}
