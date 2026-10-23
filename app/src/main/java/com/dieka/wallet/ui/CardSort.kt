package com.dieka.wallet.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.dieka.wallet.data.BankCard
import kotlin.math.abs

@Composable
fun CardSort(cards: List<BankCard>, busy: Boolean, open: Boolean, onDismiss: () -> Unit, onClosed: () -> Unit, onSave: (List<String>) -> Unit) {
    val list = rememberLazyListState()
    val order = remember { DragOrder(cards, list) }
    val haptics = LocalWalletHaptics.current
    val density = LocalDensity.current
    val edge = with(density) { 72.dp.toPx() }
    val speed = with(density) { 700.dp.toPx() }
    val enabled by rememberUpdatedState(!busy && open)

    // Scroll on animation frames, so dragging at either edge also works with long card collections.
    LaunchedEffect(order.activeId, busy, open) {
        if (!enabled) order.end()
        if (order.activeId == null) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (order.activeId != null) {
            val now = withFrameNanos { it }
            val seconds = ((now - previous) / 1_000_000_000f).coerceAtMost(.05f)
            previous = now
            val info = list.layoutInfo
            val distance = when {
                order.center < info.viewportStartOffset + edge -> -(1f - (order.center - info.viewportStartOffset).coerceAtLeast(0f) / edge)
                order.center > info.viewportEndOffset - edge -> 1f - (info.viewportEndOffset - order.center).coerceAtLeast(0f) / edge
                else -> 0f
            }.coerceIn(-1f, 1f)
            if (distance != 0f) {
                list.scrollBy(distance * speed * seconds)
                order.relocate()
            }
        }
    }

    MotionDialog(open, onDismiss = { if (!busy) onDismiss() }, onClosed = onClosed) {
        Surface(Modifier.fillMaxSize(), color = Paper) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PressIconButton(onClick = onDismiss, enabled = open && !busy) { Icon(Icons.Outlined.Close, "取消排序") }
                    Text("卡片排序", Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { onSave(order.cards.map { it.id }) }, enabled = open && !busy && order.activeId == null) {
                        Text(if (busy) "保存中…" else "完成", fontWeight = FontWeight.Bold)
                    }
                }
                Text("拖动右侧手柄，调整全部卡片顺序", Modifier.padding(start = 20.dp, bottom = 14.dp), fontSize = 12.sp, color = Muted)
                LazyColumn(
                    state = list,
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("sort-list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)
                ) {
                    items(order.cards, key = { it.id }) { card ->
                        val dragging = order.activeId == card.id
                        val currentIndex = order.cards.indexOfFirst { it.id == card.id }
                        val offset = if (dragging) {
                            val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == card.id }
                            if (item == null) 0f else order.center - item.offset - item.size / 2f
                        } else 0f
                        val translation by animateFloatAsState(offset, if (dragging) snap() else spring(stiffness = 550f), label = "drop")
                        val scale by animateFloatAsState(if (dragging) 1.025f else 1f, spring(stiffness = 550f), label = "lift")
                        val elevation by animateFloatAsState(if (dragging) 12f else 0f, label = "drag-shadow")
                        Surface(
                            modifier = Modifier.fillMaxWidth().height(86.dp)
                                .animateItem(placementSpec = if (dragging) null else spring(stiffness = 450f))
                                .zIndex(if (dragging) 1f else 0f)
                                .graphicsLayer { translationY = translation; scaleX = scale; scaleY = scale }
                                .testTag("sort-${card.id}")
                                .semantics {
                                    contentDescription = "第 ${currentIndex + 1} 张，${card.bank}，${card.title}，尾号 ${card.lastFour}"
                                    customActions = buildList {
                                        if (currentIndex > 0) add(CustomAccessibilityAction("上移") { (enabled && order.move(card.id, -1)).also { if (it) haptics.perform(TouchEvent.SortSettled) } })
                                        if (currentIndex < order.cards.lastIndex) add(CustomAccessibilityAction("下移") { (enabled && order.move(card.id, 1)).also { if (it) haptics.perform(TouchEvent.SortSettled) } })
                                    }
                                },
                            color = Color.White, shape = RoundedCornerShape(18.dp), shadowElevation = elevation.dp
                        ) {
                            Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(5.dp).height(40.dp).background(Brush.verticalGradient(cardColors[card.theme.coerceIn(0, 5)]), RoundedCornerShape(3.dp)))
                                Text("${currentIndex + 1}".padStart(2, '0'), Modifier.padding(horizontal = 12.dp), fontSize = 12.sp, color = Muted)
                                Column(
                                    Modifier.weight(1f).fillMaxHeight().pointerInput(card.id) {
                                        // Separate hit areas: long-pressing text and dragging the handle cannot compete.
                                        var ownsDrag = false
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                ownsDrag = enabled && order.start(card.id)
                                            },
                                            onDrag = { change, delta -> if (ownsDrag) { change.consume(); order.drag(delta.y) } },
                                            onDragEnd = { if (ownsDrag && order.end()) haptics.perform(TouchEvent.SortSettled); ownsDrag = false },
                                            onDragCancel = { if (ownsDrag) order.end(); ownsDrag = false }
                                        )
                                    },
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(card.bank, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(5.dp))
                                    Text("${card.title} · ${card.lastFour}", fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Box(
                                    Modifier.size(52.dp, 70.dp).testTag("drag-${card.id}")
                                        .semantics { contentDescription = "拖动排序，${card.title}" }
                                        .pointerInput(card.id) {
                                            var ownsDrag = false
                                            detectDragGestures(
                                                onDragStart = {
                                                    // Handle is vertically centered in the row. Anchor the card at its center.
                                                    ownsDrag = enabled && order.start(card.id)
                                                },
                                                onDrag = { change, delta -> if (ownsDrag) { change.consume(); order.drag(delta.y) } },
                                                onDragEnd = { if (ownsDrag && order.end()) haptics.perform(TouchEvent.SortSettled); ownsDrag = false },
                                                onDragCancel = { if (ownsDrag) order.end(); ownsDrag = false }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Outlined.DragHandle, null, tint = Leaf) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Stable
private class DragOrder(initial: List<BankCard>, private val list: LazyListState) {
    var cards by mutableStateOf(initial); private set
    var activeId by mutableStateOf<String?>(null); private set
    var center by mutableFloatStateOf(0f); private set
    private var startOrder = emptyList<String>()

    fun start(id: String): Boolean {
        if (activeId != null) return false
        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id } ?: return false
        activeId = id
        startOrder = cards.map { it.id }
        center = item.offset + item.size / 2f
        return true
    }

    fun drag(delta: Float) { center += delta; relocate() }

    fun relocate() {
        val id = activeId ?: return
        val current = cards.indexOfFirst { it.id == id }
        // A second pointer event can arrive before LazyColumn has measured the previous move.
        if (list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }?.index != current) return
        val target = list.layoutInfo.visibleItemsInfo
            .filter { it.key != id }
            .filter { (it.index > current && center >= it.offset + it.size / 2f) || (it.index < current && center <= it.offset + it.size / 2f) }
            .minByOrNull { abs(center - it.offset - it.size / 2f) } ?: return
        // Keep the viewport stationary when the first visible item changes identity.
        if (current == list.firstVisibleItemIndex || target.index == list.firstVisibleItemIndex) {
            list.requestScrollToItem(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
        }
        cards = cards.toMutableList().apply { add(target.index, removeAt(current)) }
    }

    fun move(id: String, delta: Int): Boolean {
        if (activeId != null) return false
        val from = cards.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to !in cards.indices) return false
        cards = cards.toMutableList().apply { add(to, removeAt(from)) }
        return true
    }

    fun end(): Boolean {
        val changed = activeId != null && startOrder != cards.map { it.id }
        activeId = null
        return changed
    }
}
