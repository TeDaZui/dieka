@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.dieka.wallet.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy

@Composable
fun Modifier.pressScale(source: MutableInteractionSource, amount: Float = .91f): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) amount else 1f,
        spring(dampingRatio = .75f, stiffness = 650f), label = "press"
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

@Composable
fun PressIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    IconButton(onClick, modifier.pressScale(source), enabled, interactionSource = source, content = content)
}

/** Keep the window mounted until exit finishes; lock/background removals can still be immediate. */
@Composable
fun MotionDialog(open: Boolean, onDismiss: () -> Unit, onClosed: () -> Unit = {}, compact: Boolean = false, content: @Composable () -> Unit) {
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = open
    val closed by rememberUpdatedState(onClosed)
    LaunchedEffect(visibility.isIdle, visibility.currentState, open) {
        if (visibility.isIdle && !visibility.currentState && !open) closed()
    }
    if (visibility.currentState || visibility.targetState) {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
            usePlatformDefaultWidth = compact, securePolicy = SecureFlagPolicy.SecureOff, decorFitsSystemWindows = false
        )) {
            AnimatedVisibility(
                visibleState = visibility,
                enter = fadeIn(tween(220)) + slideInVertically(spring(dampingRatio = .92f, stiffness = 360f)) { it / 18 } + scaleIn(tween(260), initialScale = .975f),
                exit = fadeOut(tween(180)) + slideOutVertically(tween(240, easing = FastOutSlowInEasing)) { it / 20 } + scaleOut(tween(220), targetScale = .985f)
            ) { content() }
        }
    }
}

/** Retain the sheet until programmatic dismissals have completed their exit animation. */
@Composable
fun MotionSheet(open: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    var mounted by remember { mutableStateOf(open) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(open) {
        if (open) { mounted = true; state.show() }
        else if (mounted) { state.hide(); mounted = false }
    }
    if (mounted) ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Paper, content = content)
}

@Composable
fun MotionAlert(open: Boolean, onDismiss: () -> Unit, title: String, confirm: String = "关闭", content: @Composable ColumnScope.() -> Unit) {
    MotionDialog(open, onDismiss, compact = true) {
        Surface(shape = RoundedCornerShape(28.dp), color = Paper) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                if (title.isNotEmpty()) { Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(18.dp)) }
                content()
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(confirm) }
            }
        }
    }
}
