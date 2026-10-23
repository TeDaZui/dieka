package com.dieka.wallet.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dieka.wallet.data.ExpiringCard

@Composable
internal fun ExpiryBanner(entries: List<ExpiringCard>, onClick: () -> Unit) {
    AnimatedVisibility(entries.isNotEmpty()) {
        entries.firstOrNull()?.let { upcoming ->
            Surface(Modifier.fillMaxWidth().padding(bottom = 6.dp).testTag("expiry-banner").clickable(onClick = onClick),
                color = Pale, shape = RoundedCornerShape(10.dp)) {
                Row(Modifier.heightIn(min = 40.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Schedule, null, Modifier.size(16.dp), tint = Leaf)
                    Spacer(Modifier.width(6.dp))
                    Text(upcoming.card.bank, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
                    Text(" · 尾号 ${upcoming.card.lastFour} · ${upcoming.timing}", fontSize = 11.sp, maxLines = 1)
                    if (entries.size > 1) Text("  另有 ${entries.size - 1} 张", fontSize = 11.sp, color = Leaf, maxLines = 1)
                }
            }
        }
    }
}
