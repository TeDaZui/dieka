package com.dieka.wallet.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dieka.wallet.data.releaseNotes

@Composable
fun ReleaseNotesSheet(open: Boolean, onDismiss: () -> Unit) {
    MotionSheet(open, onDismiss) {
        Text("更新日志", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(.78f).testTag("release-notes"),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(releaseNotes, key = { it.version }) { note ->
                var expanded by remember { mutableStateOf(note.version == releaseNotes.first().version) }
                Surface(shape = MaterialTheme.shapes.large) {
                    Column(Modifier.fillMaxWidth().animateContentSize().padding(16.dp)) {
                        Text("v${note.version}${if (note == releaseNotes.first()) " · 当前版本" else ""}",
                            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 8.dp), fontWeight = FontWeight.SemiBold)
                        AnimatedVisibility(expanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                note.changes.forEach { Text("· $it", fontSize = 14.sp, color = Muted) }
                            }
                        }
                    }
                }
            }
        }
    }
}
