package com.dieka.wallet

import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.dieka.wallet.data.*
import com.dieka.wallet.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream

class CardDesignTest {
    @get:Rule val compose = createComposeRule()

    @Test fun customImageHasNoTextOverlayEvenWithPopulatedDesign() {
        val bitmap = Bitmap.createBitmap(160, 100, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        val card = BankCard.demos().first().copy(cover = Base64.encodeToString(bytes, Base64.NO_WRAP))
        compose.setContent { DiekaTheme { CardFace(card, Modifier.fillMaxWidth().height(240.dp)) } }
        compose.waitUntil { compose.onAllNodesWithTag("custom-cover").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("custom-cover").assertIsDisplayed()
        compose.onNodeWithText(card.bank).assertDoesNotExist()
        compose.onNodeWithText(card.title).assertDoesNotExist()
        compose.onNodeWithText(card.maskedNumber).assertDoesNotExist()
        compose.onNodeWithText("VISA").assertDoesNotExist()
        compose.onNodeWithText("DEMO").assertDoesNotExist()
    }

    @Test fun blankDesignDoesNotRenderAccountData() {
        val card = BankCard(bank = "Account Bank", title = "Account Title", number = "4111111111111111")
        compose.setContent { DiekaTheme { CardFace(card, Modifier.fillMaxWidth().height(240.dp)) } }
        compose.onNodeWithText("Account Bank").assertDoesNotExist()
        compose.onNodeWithText("Account Title").assertDoesNotExist()
        compose.onNodeWithText(card.maskedNumber).assertDoesNotExist()
    }

    @Test fun designAndAccountSurviveSerializationIndependently() {
        val card = BankCard.demos().first().copy(design = CardDesign(bank = "Custom", number = "NO. 007"))
        val decoded = CardCodec.decode(CardCodec.encode(card))
        assertEquals(card, decoded)
        assertEquals(card.design, CardCodec.decode(CardCodec.encode(card.copy(bank = "Changed", number = "5555555555554444"))).design)
        val blank = card.copy(design = CardDesign())
        assertEquals(CardDesign(), CardCodec.decode(CardCodec.encode(blank)).design)
    }

    @Test fun legacyCardsRetainAppearanceAndDetails() {
        val old = BankCard.demos().first()
        val record = CardCodec.encode(old).apply { remove("design") }
        val migrated = CardCodec.decode(record)
        assertEquals(old.number, migrated.number)
        assertEquals(old.cvv, migrated.cvv)
        assertEquals(CardDesign.legacy(old), migrated.design)
        assertEquals(migrated, CardCodec.decode(CardCodec.encode(migrated)))
    }
}
