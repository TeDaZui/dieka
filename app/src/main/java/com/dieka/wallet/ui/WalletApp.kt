@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.dieka.wallet.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dieka.wallet.WalletViewModel
import com.dieka.wallet.data.BankCard
import com.dieka.wallet.data.InstalledApps
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun WalletApp(model: WalletViewModel, authenticate: (() -> Unit) -> Unit) {
    ProvideWalletHaptics(model.hapticsEnabled) { WalletContent(model, authenticate) }
}

@Composable
private fun WalletContent(model: WalletViewModel, authenticate: (() -> Unit) -> Unit) {
    val context = LocalContext.current
    var search by model.searchState
    var searching by model.searchingState
    var filter by model.filterState
    var expanded by model.layoutState
    var privacy by remember { mutableStateOf(false) }
    var sizes by remember { mutableStateOf(false) }
    var expiryList by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(false) }
    var editorMounted by remember { mutableStateOf(false) }
    var editorCard by remember { mutableStateOf<BankCard?>(null) }
    var sortMounted by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val visible = model.cards.filter {
        (filter == 0 || (filter == 1 && !it.debit) || (filter == 2 && it.debit)) &&
            (search.isBlank() || it.bank.contains(search, true) || it.title.contains(search, true) || it.lastFour.contains(search))
    }
    val add: () -> Unit = {
        if (model.demo || model.locked) authenticate { model.unlock(addAfter = true) }
        else { model.editing = null; model.showEditor = true }
    }
    val closeFocus: () -> Unit = { model.focus(null) }
    LaunchedEffect(model.notice) {
        val notice = model.notice ?: return@LaunchedEffect
        if (model.showEditor || model.showSort || model.showTrash || model.linkingCardId != null) return@LaunchedEffect
        snack.currentSnackbarData?.dismiss()
        val result = snack.showSnackbar(notice.text, actionLabel = notice.undoId?.let { "撤销" },
            duration = if (notice.undoId == null) SnackbarDuration.Short else SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed && notice.undoId != null) authenticate { model.restore(notice.undoId) }
        if (model.notice == notice) model.notice = null
    }
    LaunchedEffect(model.revealed, model.selectedId) {
        if (model.revealed) { delay(30_000); model.revealed = false }
    }
    LaunchedEffect(model.showEditor, model.locked) {
        if (model.locked) { editorMounted = false; editorCard = null }
        else if (model.showEditor) { editorCard = model.editing; editorMounted = true }
    }
    LaunchedEffect(model.showSort, model.locked) {
        if (model.locked) sortMounted = false else if (model.showSort) sortMounted = true
    }
    LaunchedEffect(model.locked) { if (model.locked) { expiryList = false; sizes = false; more = false } }
    BackHandler(model.selectedId != null && !model.showEditor && !model.showSort && !model.showTrash && !expiryList && model.linkingCardId == null && !history) {
        if (model.infoOpen) { model.infoOpen = false; model.revealed = false } else closeFocus()
    }
    Scaffold(containerColor = Paper, snackbarHost = { SnackbarHost(snack) }, floatingActionButton = {
        AnimatedVisibility(!model.locked && model.cards.isNotEmpty(), enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
            val source = remember { MutableInteractionSource() }
            FloatingActionButton(onClick = { closeFocus(); expanded = !expanded },
                modifier = Modifier.size(60.dp).pressScale(source).testTag("layout-toggle"),
                shape = CircleShape, containerColor = Ink, contentColor = Paper, interactionSource = source) {
                Crossfade(expanded, animationSpec = tween(180), label = "layout-icon") { full ->
                    Icon(if (full) Icons.Outlined.Layers else Icons.Outlined.ViewAgenda,
                        if (full) "堆叠卡片" else "展开全部卡片", Modifier.size(25.dp))
                }
            }
        }
    }) { inset ->
        Box(Modifier.fillMaxSize().padding(inset), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 600.dp).fillMaxSize().padding(horizontal = 8.dp)) {
                Row(Modifier.fillMaxWidth().height(52.dp).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("我的卡包", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    if (!model.locked) Text("  ${model.cards.size}", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    if (!model.locked) PressIconButton(onClick = { closeFocus(); searching = !searching; if (!searching) search = "" }) {
                        Icon(Icons.Outlined.Search, "搜索卡片", Modifier.size(21.dp))
                    }
                    Box {
                        PressIconButton(onClick = { more = true }) { Icon(Icons.Outlined.MoreHoriz, "更多选项") }
                        DropdownMenu(more, { more = false }) {
                            if (!model.locked) {
                                DropdownMenuItem(text = { Text("自定义排序") }, leadingIcon = { Icon(Icons.Outlined.SwapVert, null) },
                                    enabled = model.cards.size > 1 && !model.busy, onClick = { more = false; closeFocus(); model.showSort = true })
                                DropdownMenuItem(text = { Text("卡片大小") }, leadingIcon = { Icon(Icons.Outlined.PhotoSizeSelectLarge, null) },
                                    onClick = { more = false; sizes = true })
                                DropdownMenuItem(text = { Text("回收站") }, leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) },
                                    onClick = { more = false; model.refreshTime(); model.showTrash = true })
                            }
                            if (!model.demo && !model.locked) DropdownMenuItem(text = { Text("锁定卡包") }, onClick = { more = false; model.lock() })
                            DropdownMenuItem(text = { Text("轻触反馈") }, onClick = { model.updateHaptics(!model.hapticsEnabled) },
                                trailingIcon = { Switch(model.hapticsEnabled, onCheckedChange = model::updateHaptics, modifier = Modifier.testTag("haptics-toggle")) })
                            DropdownMenuItem(text = { Text("更新日志") }, onClick = { more = false; history = true })
                            DropdownMenuItem(text = { Text("隐私与安全") }, onClick = { more = false; privacy = true })
                        }
                    }
                    PressIconButton(onClick = add, enabled = !model.busy) { Icon(Icons.Outlined.Add, "添加银行卡") }
                }
                if (model.locked) {
                    Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Outlined.Fingerprint, null, Modifier.size(64.dp), tint = Leaf)
                        Spacer(Modifier.height(24.dp))
                        Text("你的卡包，已安心收好", fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = { authenticate { model.unlock() } }, enabled = !model.busy) { Text("解锁我的卡包") }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        listOf("全部", "信用卡", "储蓄卡").forEachIndexed { i, label ->
                            FilterChip(selected = filter == i, onClick = { closeFocus(); filter = i }, label = { Text(label, fontSize = 12.sp) }, border = null,
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Ink, selectedLabelColor = Paper), shape = RoundedCornerShape(30.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                    }
                    AnimatedVisibility(searching, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        OutlinedTextField(search, { closeFocus(); search = it }, Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            placeholder = { Text("银行、卡片名称或尾号", fontSize = 13.sp) }, singleLine = true, shape = RoundedCornerShape(14.dp))
                    }
                    val expiring = model.expiring
                    ExpiryBanner(expiring) { expiryList = true }
                    if (visible.isEmpty()) {
                        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Outlined.CreditCard, null, Modifier.size(48.dp), tint = Leaf)
                            Spacer(Modifier.height(16.dp))
                            Text(if (model.cards.isEmpty()) "从第一张卡开始" else "没有找到匹配的卡片")
                            TextButton(onClick = { if (model.cards.isEmpty()) add() else { search = ""; filter = 0 } }) {
                                Text(if (model.cards.isEmpty()) "添加银行卡" else "查看全部卡片")
                            }
                        }
                    } else CardBrowser(visible, expanded, model.compactCount, model.selectedId, model.infoOpen,
                        model.revealed, model.demo, model.busy, onFocus = { model.focus(it) },
                        onInfo = { model.infoOpen = !model.infoOpen; model.revealed = false },
                        onReveal = { model.revealed = !model.revealed },
                        onEdit = { card -> model.revealed = false; model.editing = card; model.showEditor = true },
                        onDelete = { card -> authenticate { model.delete(card) } }, modifier = Modifier.weight(1f).fillMaxWidth(),
                        onLink = { model.linkingCardId = it.id }, locateRequest = model.locateRequest,
                        onApp = { card -> card.appLink?.let { link ->
                            if (!InstalledApps.launch(context, link)) {
                                model.message = "应用已卸载或暂时无法打开，请重新选择应用。"
                                model.linkingCardId = card.id
                            }
                        } })
                }
            }
            if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter), color = Leaf)
        }
    }
    if (editorMounted && !model.locked) CardEditor(editorCard, model.busy,
        onDismiss = { model.showEditor = false; model.editing = null }, onSave = { card -> authenticate { model.save(card) } },
        onError = { model.message = it }, open = model.showEditor, onClosed = { editorMounted = false; editorCard = null })
    if (sortMounted && !model.locked) CardSort(model.cards, model.busy, model.showSort,
        onDismiss = { model.showSort = false }, onClosed = { sortMounted = false }, onSave = { ids ->
            if (model.demo || ids == model.cards.map { it.id }) model.reorder(ids) else authenticate { model.reorder(ids) }
        })
    MotionAlert(sizes, { sizes = false }, "卡片大小") {
        Column {
            listOf(3 to "大 · 每屏约 3 张", 4 to "标准 · 每屏约 4 张", 5 to "小 · 每屏约 5 张").forEach { (count, title) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("density-$count").clickable {
                    closeFocus(); model.updateCompactCount(count); sizes = false
                }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(model.compactCount == count, onClick = null); Spacer(Modifier.width(10.dp)); Text(title)
                }
            }
            Text("用于全部展开状态", color = Muted, fontSize = 12.sp)
        }
    }
    if (!model.locked) MotionSheet(expiryList, { expiryList = false }) {
        Text("即将到期", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 28.dp)) {
            items(model.expiring, key = { it.card.id }) { entry ->
                ListItem(headlineContent = { Text("${entry.card.bank} · ${entry.card.lastFour}") }, supportingContent = { Text(entry.timing) },
                    modifier = Modifier.clickable { expiryList = false; search = ""; filter = 0; model.focus(entry.card, locate = true) },
                    colors = ListItemDefaults.colors(containerColor = Paper))
            }
        }
    }
    if (!model.locked) MotionSheet(model.showTrash, { model.showTrash = false }) {
        Text("回收站", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (model.trash.isEmpty()) Text("回收站是空的", Modifier.padding(24.dp), color = Muted)
        LazyColumn(Modifier.fillMaxWidth().testTag("trash-list"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(model.trash, key = { it.id }) { entry ->
                Surface(Modifier.fillMaxWidth().testTag("trash-${entry.card.id}"), shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CardArtwork(entry.card, Modifier.width(82.dp).aspectRatio(1.586f))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.card.bank, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("尾号 ${entry.card.lastFour}", fontSize = 12.sp, color = Muted)
                            val until = Instant.ofEpochMilli(entry.recoverUntil).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
                            Text("可恢复至 $until", fontSize = 10.sp, color = Muted)
                        }
                        TextButton(onClick = { authenticate { model.restore(entry.id) } }, enabled = !model.busy) { Text("恢复") }
                    }
                }
            }
        }
    }
    MotionAlert(privacy, { privacy = false }, "隐私与安全", "知道了") {
        Text("卡片资料、卡面和回收站均在本机加密保存，不联网、不进行云备份。\n\n支持截图及系统近期任务预览。其他信息显示 30 秒后自动隐藏，约 5 分钟内免重复验证，后台超过 5 分钟后锁定。\n\n移除的卡片七天内可恢复。卸载应用、清除数据或重置设备锁屏可能导致卡库无法恢复。", fontSize = 14.sp)
    }
    ReleaseNotesSheet(history, { history = false })
    AppLinkSheet(model.linkingCardId != null && !model.locked, model.cards.firstOrNull { it.id == model.linkingCardId }?.appLink,
        model.busy, { model.linkingCardId = null }, { link ->
            model.linkingCardId?.let { id ->
                if (model.demo) model.linkApp(id, link) else authenticate { model.linkApp(id, link) }
            }
        })
    if ((model.showEditor || model.showSort || model.showTrash || model.linkingCardId != null) && model.notice?.error == true) {
        AlertDialog(onDismissRequest = { model.notice = null }, text = { Text(model.notice?.text.orEmpty()) },
            confirmButton = { TextButton(onClick = { model.notice = null }) { Text("知道了") } })
    }
}
