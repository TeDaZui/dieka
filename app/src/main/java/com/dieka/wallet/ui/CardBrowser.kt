@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.dieka.wallet.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dieka.wallet.data.BankCard
import com.dieka.wallet.data.DeckMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun CardBrowser(
    cards: List<BankCard>, expanded: Boolean, count: Int, selectedId: String?, infoOpen: Boolean,
    revealed: Boolean, demo: Boolean, busy: Boolean, onFocus: (BankCard?) -> Unit,
    onInfo: () -> Unit, onReveal: () -> Unit, onEdit: (BankCard) -> Unit, onDelete: (BankCard) -> Unit,
    modifier: Modifier = Modifier, onApp: (BankCard) -> Unit = {}, onLink: (BankCard) -> Unit = {}, locateRequest: Int = 0
) {
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val feedback = LocalWalletHaptics.current
    val focus by rememberUpdatedState(onFocus)
    val selectedIndex = cards.indexOfFirst { it.id == selectedId }
    var active by remember { mutableStateOf(false) }
    var committing by remember { mutableStateOf(false) }
    var originId by remember { mutableStateOf<String?>(null) }
    var shift by remember { mutableFloatStateOf(0f) }
    var edge by remember { mutableFloatStateOf(0f) }
    var motion by remember { mutableStateOf<Job?>(null) }
    var movementId by remember { mutableIntStateOf(0) }
    val layoutMix by animateFloatAsState(if (expanded) 1f else 0f, spring(.92f, 340f), label = "layout-density")
    LaunchedEffect(locateRequest) {
        if (locateRequest > 0 && selectedIndex >= 0) list.animateScrollToItem(selectedIndex)
    }
    LaunchedEffect(selectedId, cards.map { it.id }, expanded) {
        if (active && !committing && selectedId != originId) {
            movementId++; motion?.cancel(); active = false; shift = 0f; edge = 0f
        }
    }
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val fullWidth = maxWidth
        val smallHeight by animateDpAsState(minOf(fullWidth / 1.586f,
            ((maxHeight - 80.dp - 12.dp * (count - 1)) / count).coerceAtLeast(96.dp)), spring(.94f, 340f), label = "card-density-size")
        val fullHeightPx = with(density) { (fullWidth / 1.586f).toPx() }
        val stride = with(density) { (72.dp * (1f - layoutMix) + (smallHeight + 12.dp) * layoutMix).toPx() }
        val velocityThreshold = with(density) { 800.dp.toPx() }
        fun moveTo(value: Float) {
            val next = value.coerceIn(-1f, 1f)
            val difference = next - shift
            shift = next
            // The child owns this drag. Move the list by the collapsed row distance so the
            // incoming card lands at the departing card's viewport position, without a second scroll animation.
            list.dispatchRawDelta(difference * stride)
        }
        fun settle(velocity: Float, forcedStep: Int? = null) {
            val index = cards.indexOfFirst { it.id == originId }
            if (index < 0) return
            val step = forcedStep ?: DeckMotion.target(shift, -velocity / velocityThreshold, index > 0, index < cards.lastIndex)
            val ticket = ++movementId
            motion?.cancel()
            motion = scope.launch {
                try {
                    Animatable(shift).animateTo(step.toFloat(), spring(dampingRatio = .94f, stiffness = 330f),
                        initialVelocity = (-velocity / fullHeightPx).coerceIn(-5f, 5f)) { moveTo(value) }
                    if (edge != 0f) Animatable(edge).animateTo(0f, spring(.8f, 400f)) { edge = value }
                    if (step != 0) {
                        committing = true
                        val next = cards[index + step]
                        originId = next.id; shift = 0f; focus(next)
                        feedback.perform(TouchEvent.CardSettled)
                        // Sync per-row animation values while still rendering the exact gesture position.
                        repeat(2) { withFrameNanos { } }
                    }
                } finally { if (ticket == movementId) { active = false; committing = false; shift = 0f; edge = 0f } }
            }
        }
        LazyColumn(Modifier.fillMaxSize().testTag("card-list"), state = list,
            verticalArrangement = Arrangement.spacedBy(12.dp * layoutMix),
            contentPadding = PaddingValues(top = 3.dp, bottom = 84.dp)) {
            itemsIndexed(cards, key = { _, card -> card.id }) { index, card ->
                val selected = card.id == selectedId
                val currentIndex = cards.indexOfFirst { it.id == originId }
                val target = if (active && currentIndex >= 0) DeckMotion.expansion(index, currentIndex, shift) else if (selected) 1f else 0f
                val expansion = remember { Animatable(if (selected) 1f else 0f) }
                LaunchedEffect(target, active) {
                    if (active) expansion.snapTo(target) else expansion.animateTo(target, spring(.94f, 340f))
                }
                val amount = (if (active) target else expansion.value).coerceIn(0f, 1f)
                val width = fullWidth + (smallHeight * 1.586f - fullWidth) * layoutMix * (1f - amount)
                val source = remember { MutableInteractionSource() }
                val gestureEnabled = selected && !busy && !committing
                Layout(modifier = Modifier.fillMaxWidth()
                    .animateItem(fadeInSpec = tween(180), placementSpec = null, fadeOutSpec = tween(140))
                    .testTag("row-${card.id}"), content = {
                    CardArtwork(card, Modifier.width(width).aspectRatio(1.586f)
                        .graphicsLayer { translationY = if (selected) edge else 0f }
                        .pressScale(source, .99f).testTag("card-${card.id}")
                        .semantics {
                            contentDescription = "${card.bank}，${card.title}，尾号${card.lastFour}"
                            stateDescription = if (selected) "已展开" else "已收起"
                            if (selected) customActions = buildList {
                                fun action(step: Int) { motion?.cancel(); originId = card.id; active = true; settle(0f, step) }
                                if (index > 0) add(CustomAccessibilityAction("上一张卡片") { action(-1); true })
                                if (index < cards.lastIndex) add(CustomAccessibilityAction("下一张卡片") { action(1); true })
                            }
                        }
                        .draggable(rememberDraggableState { delta ->
                            val proposed = shift - delta / fullHeightPx
                            if ((index == 0 && proposed < 0f) || (index == cards.lastIndex && proposed > 0f)) {
                                edge = (edge + delta * .24f).coerceIn(-fullHeightPx * .08f, fullHeightPx * .08f)
                            } else { edge = 0f; moveTo(proposed) }
                        }, Orientation.Vertical, enabled = gestureEnabled,
                            onDragStarted = { movementId++; motion?.cancel(); originId = card.id; active = true },
                            onDragStopped = { settle(it) })
                        .clickable(interactionSource = source, indication = null, enabled = !busy && !active) { focus(if (selected) null else card) })
                    Column(Modifier.fillMaxWidth().graphicsLayer { alpha = amount }) {
                        if (amount > .001f || selected) {
                            CardActions(card, enabled = selected && !active && !busy, editable = !demo,
                                onInfo = onInfo, onApp = { onApp(card) }, onLink = { onLink(card) },
                                onEdit = { onEdit(card) }, onDelete = { onDelete(card) })
                            AnimatedVisibility(selected && infoOpen) { CardInformation(card, revealed, onReveal) }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }) { children, constraints ->
                    val loose = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
                    val art = children[0].measure(loose)
                    val details = children[1].measure(loose)
                    val peek = 72.dp.toPx()
                    val artSpace = if (index == cards.lastIndex) art.height.toFloat() else
                        (peek + (art.height - peek) * amount) * (1f - layoutMix) + art.height * layoutMix
                    layout(constraints.maxWidth, (artSpace + details.height * amount).roundToInt()) {
                        art.place((constraints.maxWidth - art.width) / 2, 0)
                        details.place(0, art.height)
                    }
                }
            }
        }
    }
}

@Composable
private fun CardActions(card: BankCard, enabled: Boolean, editable: Boolean, onInfo: () -> Unit,
    onApp: () -> Unit, onLink: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) menu = false }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onInfo, enabled = enabled, modifier = Modifier.weight(1f).testTag("card-info")) { Text("卡片信息", fontSize = 13.sp) }
        val source = remember { MutableInteractionSource() }
        Box(Modifier.weight(1.5f).heightIn(min = 48.dp).pressScale(source)
            .testTag("card-app").combinedClickable(enabled = enabled, interactionSource = source, indication = null,
                hapticFeedbackEnabled = false, onLongClickLabel = "更改关联应用", onLongClick = onLink,
                onClick = { if (card.appLink == null) onLink() else onApp() }), contentAlignment = Alignment.Center) {
            Text(card.appLink?.let { "打开${it.displayName}" } ?: "关联应用", Modifier.padding(horizontal = 8.dp),
                color = if (enabled) Leaf else Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box {
            PressIconButton(onClick = { menu = true }, enabled = enabled, modifier = Modifier.testTag("card-more")) {
                Icon(Icons.Outlined.MoreHoriz, "卡片更多选项")
            }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text("编辑卡片") }, enabled = editable, onClick = { menu = false; onEdit() })
                DropdownMenuItem(text = { Text("移除", color = if (editable) MaterialTheme.colorScheme.error else Muted) }, enabled = editable,
                    onClick = { menu = false; onDelete() })
            }
        }
    }
}
@Composable
private fun CardInformation(card: BankCard, revealed: Boolean, onReveal: () -> Unit) {
    val context = LocalContext.current
    val feedback = LocalWalletHaptics.current
    Surface(Modifier.fillMaxWidth().testTag("card-information"), color = Color.White, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text("银行卡号", fontSize = 11.sp, color = Muted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(card.formattedNumber, Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontSize = 16.sp)
                PressIconButton(onClick = {
                    val clip = ClipData.newPlainText("银行卡号", card.number.filter { it in '0'..'9' }).apply {
                        description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                    }
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
                    feedback.perform(TouchEvent.Copied)
                    if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, "卡号已复制", Toast.LENGTH_SHORT).show()
                }) { Icon(Icons.Outlined.ContentCopy, "复制卡号", Modifier.size(19.dp)) }
            }
            HorizontalDivider(color = Paper)
            TextButton(onClick = onReveal, modifier = Modifier.align(Alignment.End)) {
                Text(if (revealed) "隐藏其他信息" else "显示其他信息", fontSize = 12.sp)
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("有效期" to card.expiry, "安全码" to card.cvv, "持卡人" to card.holder).forEach { (label, value) ->
                    Column(Modifier.weight(1f)) {
                        Text(label, color = Muted, fontSize = 11.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(if (revealed) value.ifEmpty { "未填写" } else "••••", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
