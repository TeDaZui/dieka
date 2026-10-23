package com.dieka.wallet.ui

import android.view.HapticFeedbackConstants
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView

enum class TouchEvent { CardSettled, SortSettled, Copied }

class WalletHaptics(private val enabled: () -> Boolean, private val feedback: (TouchEvent) -> Unit) {
    fun perform(event: TouchEvent) { if (enabled()) feedback(event) }
}

val LocalWalletHaptics = staticCompositionLocalOf { WalletHaptics({ false }, {}) }

@Composable
fun ProvideWalletHaptics(enabled: Boolean, content: @Composable () -> Unit) {
    val view = LocalView.current
    val current by rememberUpdatedState(enabled)
    val feedback = remember(view) { WalletHaptics({ current }) { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) } }
    CompositionLocalProvider(LocalWalletHaptics provides feedback, content = content)
}
