package com.dieka.wallet.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dieka.wallet.data.BankCard
import com.dieka.wallet.data.DesignField
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val cardColors = listOf(
    listOf(Color(0xFFEDE4CE), Color(0xFFB8A778)),
    listOf(Color(0xFFB6D9D7), Color(0xFF3C7779)),
    listOf(Color(0xFF6E8CC5), Color(0xFF203C6E)),
    listOf(Color(0xFFE9B7A4), Color(0xFFC56F66)),
    listOf(Color(0xFFC9C2DF), Color(0xFF8275A5)),
    listOf(Color(0xFFB5C5A7), Color(0xFF3D6250)),
)
val themeNames = listOf("麦田", "山海", "蓝调", "落日", "暮紫", "林间")

@Composable
fun CardFace(card: BankCard, modifier: Modifier = Modifier, demo: Boolean = false, onEditField: ((DesignField) -> Unit)? = null) {
    val palette = cardColors[card.theme.coerceIn(0, 5)]
    val foreground = if (card.theme == 0 && card.cover.isEmpty()) Color(0xFF4D4836) else Color.White
    val cover by produceState<ImageBitmap?>(null, card.cover) {
        value = null
        if (card.cover.isNotEmpty()) value = withContext(Dispatchers.Default) {
            runCatching {
                val bytes = Base64.decode(card.cover, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(
        modifier.shadow(12.dp, RoundedCornerShape(23.dp), ambientColor = Ink.copy(alpha = .18f))
            .clip(RoundedCornerShape(23.dp))
            .background(Brush.linearGradient(palette))
            .border(1.dp, Color.White.copy(alpha = .35f), RoundedCornerShape(23.dp))
    ) {
        if (card.cover.isNotEmpty()) {
            cover?.let { Image(it, "自定义卡面", Modifier.fillMaxSize().testTag("custom-cover"), contentScale = ContentScale.Fit) }
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                when (card.theme) {
                    0 -> {
                        repeat(9) { i ->
                            drawCircle(Color.White.copy(alpha = .14f), h * (.5f + i * .13f), Offset(w * .85f, h * .85f), style = Stroke(2f))
                        }
                        drawCircle(Color(0xFFF5E9BF).copy(.5f), h * .35f, Offset(w * .85f, h * .6f))
                    }
                    1, 5 -> {
                        drawCircle(Color(0xFFF4EFCB).copy(.8f), h * .17f, Offset(w * .75f, h * .34f))
                        repeat(4) { layer ->
                            val p = Path()
                            p.moveTo(0f, h)
                            for (x in 0..60) {
                                val xf = x / 60f
                                val yf = (.55f + layer * .13f) + sin(xf * 8f + layer * 2f) * .14f
                                p.lineTo(xf * w, yf * h)
                            }
                            p.lineTo(w, h); p.close()
                            drawPath(p, palette[1].copy(alpha = .18f + layer * .14f))
                        }
                    }
                    2 -> repeat(12) { i ->
                        drawCircle(Color.White.copy(alpha = .09f + i % 3 * .03f), h * (.2f + i * .12f), Offset(w, h * .85f), style = Stroke(h * .045f))
                    }
                    3 -> {
                        drawCircle(Color(0xFFFFE4B1), h * .31f, Offset(w * .75f, h * .53f))
                        repeat(7) { i ->
                            drawOval(palette[1].copy(.12f + i * .055f), Offset(-w * .2f + i * 30, h * .6f + i * 12), Size(w * 1.6f, h * .85f))
                        }
                    }
                    else -> {
                        repeat(7) { i ->
                            drawOval(Color.White.copy(.07f), Offset(w * .35f + i * 26, -h * .6f), Size(w * .5f, h * 2f), style = Stroke(22f))
                        }
                    }
                }
            }
        }
        if (card.cover.isEmpty()) Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).border(1.5.dp, foreground.copy(.8f), RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                    DesignText(card, DesignField.EMBLEM, foreground, 17.sp, onEditField)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    DesignText(card, DesignField.BANK, foreground, 16.sp, onEditField)
                    DesignText(card, DesignField.KIND, foreground.copy(.75f), 9.sp, onEditField)
                }
                DesignText(card, DesignField.BADGE, foreground.copy(.75f), 9.sp, onEditField)
            }
            Spacer(Modifier.weight(1f))
            DesignText(card, DesignField.TITLE, foreground.copy(.9f), 12.sp, onEditField)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(31.dp, 24.dp)) {
                    drawRoundRect(Brush.linearGradient(listOf(Color(0xFFEBDDAB), Color(0xFFC7B580))), cornerRadius = CornerRadius(5f))
                    drawLine(Color(0xFF9A8C64), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1f)
                    drawRoundRect(Color(0xFF9A8C64), Offset(size.width * .3f, 0f), Size(size.width * .4f, size.height), CornerRadius(4f), style = Stroke(1f))
                }
                Spacer(Modifier.width(10.dp))
                Text(")))", color = foreground.copy(.65f), fontSize = 18.sp)
                Spacer(Modifier.weight(1f))
                DesignText(card, DesignField.NETWORK, foreground, 16.sp, onEditField)
            }
            Spacer(Modifier.height(13.dp))
            DesignText(card, DesignField.NUMBER, foreground, 16.sp, onEditField)
        }
    }
}

/** Measure the whole design at a fixed size, then scale artwork and typography together. */
@Composable
fun CardArtwork(card: BankCard, modifier: Modifier = Modifier) {
    Layout(content = { CardFace(card) }, modifier = modifier) { measurables, constraints ->
        val baseWidth = 376.dp.roundToPx()
        val baseHeight = (baseWidth / 1.586f).toInt()
        val width = constraints.maxWidth
        val height = constraints.constrainHeight((width / 1.586f).toInt())
        val child = measurables.single().measure(Constraints.fixed(baseWidth, baseHeight))
        layout(width, height) {
            child.placeWithLayer(0, 0) {
                scaleX = width.toFloat() / baseWidth; scaleY = height.toFloat() / baseHeight
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

@Composable
private fun DesignText(card: BankCard, field: DesignField, color: Color, size: TextUnit, onEdit: ((DesignField) -> Unit)?) {
    val value = field.read(card.design)
    if (value.isBlank() && onEdit == null) return
    val modifier = if (onEdit == null) Modifier else Modifier
        .testTag("design-${field.name}")
        .border(1.dp, color.copy(alpha = .3f), RoundedCornerShape(4.dp))
        .clickable { onEdit(field) }.padding(horizontal = 4.dp, vertical = 3.dp)
    Text(value.ifEmpty { if (field == DesignField.EMBLEM) "+" else field.label },
        modifier = modifier, color = color, fontSize = size, maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        fontFamily = if (field == DesignField.NUMBER) FontFamily.Monospace else FontFamily.Default,
        fontWeight = if (field == DesignField.BANK || field == DesignField.NETWORK) FontWeight.SemiBold else FontWeight.Normal)
}
