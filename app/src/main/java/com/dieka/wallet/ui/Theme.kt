package com.dieka.wallet.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF26352C)
val Muted = Color(0xFF7D877F)
val Paper = Color(0xFFF4F5F0)
val Leaf = Color(0xFF526B51)
val Pale = Color(0xFFE5EBDD)

@Composable
fun DiekaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Leaf, onPrimary = Color.White, primaryContainer = Pale,
            secondaryContainer = Pale, onSecondaryContainer = Ink,
            onPrimaryContainer = Ink, background = Paper, onBackground = Ink,
            surface = Paper, onSurface = Ink, surfaceVariant = Pale,
            onSurfaceVariant = Muted, outline = Color(0xFFC7CEC2),
        ),
        content = content
    )
}
