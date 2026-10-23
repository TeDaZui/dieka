package com.dieka.wallet.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dieka.wallet.data.AppLink
import com.dieka.wallet.data.InstalledApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppLinkSheet(open: Boolean, initial: AppLink?, busy: Boolean, onDismiss: () -> Unit, onSave: (AppLink?) -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppLink>>(emptyList()) }
    var selected by remember { mutableStateOf<AppLink?>(null) }
    var name by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (open) {
            selected = initial; name = initial?.customName.orEmpty(); query = ""; loading = true; failed = false
            try {
                apps = withContext(Dispatchers.IO) { InstalledApps.list(context) }
                apps.firstOrNull { it.packageName == initial?.packageName }?.let { selected = it.copy(customName = name) }
            } catch (_: Exception) { failed = true }
            finally { loading = false }
        }
    }
    MotionSheet(open, { if (!busy) onDismiss() }) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.8f).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                PressIconButton(onClick = onDismiss, enabled = !busy) { Icon(Icons.Outlined.Close, "取消关联") }
                Text("关联应用", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = { selected?.let { onSave(it.copy(customName = name.trim())) } }, enabled = selected != null && !busy) {
                    Text(if (busy) "保存中…" else "保存关联")
                }
            }
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().testTag("app-search"),
                    singleLine = true, label = { Text("搜索手机中的应用") }, shape = MaterialTheme.shapes.medium)
                AnimatedVisibility(selected != null) {
                  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it.take(24) }, Modifier.fillMaxWidth().testTag("app-custom-name"),
                        singleLine = true, label = { Text("自定义名称（可选）") },
                        placeholder = { Text(selected?.detectedName.orEmpty()) }, shape = MaterialTheme.shapes.medium)
                    Text("按钮显示：打开${name.trim().ifEmpty { selected?.detectedName.orEmpty() }}", fontSize = 12.sp, color = Muted)
                  }
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(20.dp))
            val visible = apps.filter { it.detectedName.contains(query, ignoreCase = true) }
            if (!loading && visible.isEmpty()) Text(if (failed) "暂时无法读取应用列表，请关闭后重试" else "没有找到可打开的应用", Modifier.padding(20.dp), color = Muted)
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("app-list"), contentPadding = PaddingValues(12.dp)) {
                items(visible, key = { it.packageName }) { app ->
                    ListItem(headlineContent = { Text(app.detectedName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { AppIcon(app.packageName) }, trailingContent = { RadioButton(selected?.packageName == app.packageName, onClick = null) },
                        colors = ListItemDefaults.colors(containerColor = Paper),
                        modifier = Modifier.testTag("app-${app.packageName}").clickable(enabled = !busy) {
                            if (selected?.packageName != app.packageName) name = ""
                            selected = app
                        })
                }
            }
            if (initial != null) TextButton(onClick = { onSave(null) }, enabled = !busy, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)) {
                Text("解除关联")
            }
        }
    }
}

@Composable
private fun AppIcon(packageName: String) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                drawable.setBounds(0, 0, 96, 96); drawable.draw(Canvas(bitmap)); bitmap.asImageBitmap()
            }.getOrNull()
        }
    }
    icon?.let { Image(it, null, Modifier.size(36.dp)) } ?: Icon(Icons.Outlined.Apps, null, Modifier.size(36.dp), tint = Leaf)
}
