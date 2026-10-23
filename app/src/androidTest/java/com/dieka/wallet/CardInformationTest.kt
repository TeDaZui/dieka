package com.dieka.wallet

import android.content.ClipboardManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.dieka.wallet.data.BankCard
import com.dieka.wallet.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CardInformationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun copiesTwelveThroughNineteenDigitsWithoutGroupingSpaces() {
        var card by mutableStateOf(BankCard.demos()[0])
        compose.setContent {
            DiekaTheme { CardBrowser(listOf(card), true, 4, card.id, true, false, true, false,
                {}, {}, {}, {}, {}, Modifier.fillMaxSize()) }
        }
        for (length in 12..19) {
            val number = "1234567890123456789".take(length)
            compose.runOnIdle { card = card.copy(number = number.chunked(4).joinToString(" ")) }
            compose.onNodeWithContentDescription("复制卡号").performClick()
            compose.runOnIdle {
                val manager = InstrumentationRegistry.getInstrumentation().targetContext.getSystemService(ClipboardManager::class.java)
                assertEquals(number, manager.primaryClip!!.getItemAt(0).text.toString())
            }
        }
    }
}
