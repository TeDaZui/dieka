package com.dieka.wallet.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dieka.wallet.data.CropRegion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun CardCropper(uri: Uri, onCancel: () -> Unit, onConfirm: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preview by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var turns by remember { mutableIntStateOf(0) }
    var free by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(uri) {
        try {
            preview = withContext(Dispatchers.IO) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                    val ratio = min(1f, 2048f / max(info.size.width, info.size.height))
                    decoder.setTargetSize(max(1, (info.size.width * ratio).roundToInt()), max(1, (info.size.height * ratio).roundToInt()))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            }
        } catch (_: Exception) { error = "这张图片无法读取，请返回后选择另一张图片。" }
    }
    val rotated = remember(preview, turns) {
        preview?.let { if (turns == 0) it else Bitmap.createBitmap(it, 0, 0, it.width, it.height, Matrix().apply { postRotate(turns * 90f) }, true) }
    }
    val state = remember(rotated) { CropViewport() }
    val handleRadius = with(LocalDensity.current) { 26.dp.toPx() }
    MotionDialog(open, onDismiss = { if (!busy) open = false }, onClosed = { result?.let(onConfirm) ?: onCancel() }) {
        Surface(Modifier.fillMaxSize(), color = Ink) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PressIconButton(onClick = { open = false }, enabled = !busy) { Icon(Icons.Outlined.Close, "取消裁剪", tint = Paper) }
                    Text("裁剪卡面", Modifier.weight(1f), color = Paper, fontSize = 19.sp)
                    TextButton(enabled = rotated != null && state.ready && !busy, onClick = {
                        val normalized = state.region().unrotate(turns)
                        val rotation = turns
                        busy = true
                        scope.launch {
                            try {
                                result = withContext(Dispatchers.IO) { renderCrop(context, uri, normalized, rotation) }
                                open = false
                            } catch (_: Exception) { error = "裁剪未完成，请调整选区后重试，或重新选择图片。" }
                            finally { busy = false }
                        }
                    }) { Text(if (busy) "处理中…" else "使用图片", color = Paper) }
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (rotated == null) {
                        if (error == null) CircularProgressIndicator(color = Paper)
                    } else {
                        val image = remember(rotated) { rotated.asImageBitmap() }
                        Canvas(Modifier.fillMaxSize().testTag("crop-canvas")
                            .onSizeChanged { state.initialize(it, IntSize(rotated.width, rotated.height), free) }
                            .pointerInput(state, free, busy) {
                                if (!busy) awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val corners = listOf(state.crop.topLeft, state.crop.topRight, state.crop.bottomRight, state.crop.bottomLeft)
                                    val corner = corners.indexOfFirst { (it - down.position).getDistance() < handleRadius }
                                    var previous = down.position
                                    do {
                                        val event = awaitPointerEvent()
                                        if (event.changes.count { it.pressed } > 1) {
                                            state.transform(event.calculateZoom(), event.calculatePan(), event.calculateCentroid(useCurrent = false))
                                            previous = event.changes.firstOrNull { it.id == down.id }?.position ?: previous
                                        } else {
                                            val pointer = event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
                                            val delta = pointer.position - previous
                                            if (corner >= 0) state.resize(corner, delta, free) else state.transform(1f, delta, pointer.position)
                                            previous = pointer.position
                                        }
                                        event.changes.forEach { it.consume() }
                                    } while (event.changes.any { it.pressed })
                                }
                            }) {
                            if (state.ready) {
                                drawImage(image, dstOffset = IntOffset(state.offset.x.roundToInt(), state.offset.y.roundToInt()),
                                    dstSize = IntSize((image.width * state.scale).roundToInt(), (image.height * state.scale).roundToInt()))
                                val c = state.crop
                                val shade = Color.Black.copy(alpha = .58f)
                                drawRect(shade, Offset.Zero, Size(size.width, c.top))
                                drawRect(shade, Offset(0f, c.bottom), Size(size.width, size.height - c.bottom))
                                drawRect(shade, Offset(0f, c.top), Size(c.left, c.height))
                                drawRect(shade, Offset(c.right, c.top), Size(size.width - c.right, c.height))
                                drawRect(Color.White, c.topLeft, c.size, style = Stroke(2.dp.toPx()))
                                repeat(2) { i ->
                                    val part = (i + 1) / 3f
                                    drawLine(Color.White.copy(.4f), Offset(c.left + c.width * part, c.top), Offset(c.left + c.width * part, c.bottom))
                                    drawLine(Color.White.copy(.4f), Offset(c.left, c.top + c.height * part), Offset(c.right, c.top + c.height * part))
                                }
                                listOf(c.topLeft, c.topRight, c.bottomRight, c.bottomLeft).forEach { drawCircle(Color.White, 5.dp.toPx(), it) }
                            }
                        }
                    }
                }
                error?.let { Text(it, Modifier.padding(16.dp), color = Color(0xFFFFCCB8), fontSize = 12.sp) }
                Text("拖动边角裁剪 · 双指缩放 · 拖动图片调整位置", Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp), color = Paper.copy(.7f), fontSize = 11.sp)
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                    FilterChip(!free, { free = false; state.reset(false) }, label = { Text("银行卡比例", color = if (!free) Ink else Paper) }, enabled = !busy)
                    FilterChip(free, { free = true }, label = { Text("自由比例", color = if (free) Ink else Paper) }, enabled = !busy)
                    PressIconButton(onClick = { turns = (turns + 1) % 4 }, enabled = !busy && rotated != null) {
                        Icon(Icons.Outlined.Rotate90DegreesCw, "旋转图片", tint = Paper)
                    }
                    TextButton(onClick = { turns = 0; free = false; state.reset(false) }, enabled = !busy) { Text("重置", color = Paper) }
                }
            }
        }
    }
}

@Stable
internal class CropViewport {
    var crop by mutableStateOf(Rect.Zero); private set
    var scale by mutableFloatStateOf(1f); private set
    var offset by mutableStateOf(Offset.Zero); private set
    var ready by mutableStateOf(false); private set
    private var viewport = IntSize.Zero
    private var image = IntSize.Zero
    private var fit = 1f

    fun initialize(view: IntSize, source: IntSize, free: Boolean) {
        if (viewport == view && image == source && ready) return
        viewport = view; image = source
        reset(free)
    }
    fun reset(free: Boolean) {
        if (viewport.width == 0 || viewport.height == 0 || image.width == 0) return
        val margin = min(viewport.width, viewport.height) * .06f
        fit = min((viewport.width - margin * 2) / image.width, (viewport.height - margin * 2) / image.height)
        scale = fit
        offset = Offset((viewport.width - image.width * scale) / 2, (viewport.height - image.height * scale) / 2)
        val w = if (free) image.width * scale * .9f else min(image.width * scale * .9f, image.height * scale * .9f * 1.586f)
        val h = if (free) image.height * scale * .9f else w / 1.586f
        crop = Rect((viewport.width - w) / 2, (viewport.height - h) / 2, (viewport.width + w) / 2, (viewport.height + h) / 2)
        ready = true
    }
    fun transform(zoom: Float, pan: Offset, centroid: Offset) {
        if (!ready || !zoom.isFinite() || !centroid.x.isFinite() || !centroid.y.isFinite()) return
        val old = scale
        scale = (scale * zoom).coerceIn(max(crop.width / image.width, crop.height / image.height), max(fit * 16, scale))
        offset = centroid + (offset - centroid) * (scale / old) + pan
        constrainImage()
    }
    private fun constrainImage() {
        offset = Offset(offset.x.coerceIn(crop.right - image.width * scale, crop.left),
            offset.y.coerceIn(crop.bottom - image.height * scale, crop.top))
    }
    fun resize(corner: Int, delta: Offset, free: Boolean) {
        if (!ready) return
        val fixed = when (corner) { 0 -> crop.bottomRight; 1 -> crop.bottomLeft; 2 -> crop.topLeft; else -> crop.topRight }
        val moving = when (corner) { 0 -> crop.topLeft; 1 -> crop.topRight; 2 -> crop.bottomRight; else -> crop.bottomLeft } + delta
        val left = corner == 0 || corner == 3
        val top = corner < 2
        val bounds = Rect(max(0f, offset.x), max(0f, offset.y),
            min(viewport.width.toFloat(), offset.x + image.width * scale), min(viewport.height.toFloat(), offset.y + image.height * scale))
        val maxW = if (left) fixed.x - bounds.left else bounds.right - fixed.x
        val maxH = if (top) fixed.y - bounds.top else bounds.bottom - fixed.y
        val minSide = min(viewport.width, viewport.height) * .08f
        var w = (if (left) fixed.x - moving.x else moving.x - fixed.x).coerceIn(min(minSide, maxW), maxW)
        var h = (if (top) fixed.y - moving.y else moving.y - fixed.y).coerceIn(min(minSide, maxH), maxH)
        if (!free) {
            val drivenWidth = if (kotlin.math.abs(delta.x) > kotlin.math.abs(delta.y) * 1.586f) w else h * 1.586f
            w = min(drivenWidth, min(maxW, maxH * 1.586f)); h = w / 1.586f
        }
        crop = Rect(if (left) fixed.x - w else fixed.x, if (top) fixed.y - h else fixed.y,
            if (left) fixed.x else fixed.x + w, if (top) fixed.y else fixed.y + h)
    }
    fun region() = CropRegion(
        ((crop.left - offset.x) / (image.width * scale)).coerceIn(0f, 1f),
        ((crop.top - offset.y) / (image.height * scale)).coerceIn(0f, 1f),
        ((crop.right - offset.x) / (image.width * scale)).coerceIn(0f, 1f),
        ((crop.bottom - offset.y) / (image.height * scale)).coerceIn(0f, 1f))
}

internal fun renderCrop(context: Context, uri: Uri, region: CropRegion, turns: Int): String {
    val cropped = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
        val ratio = min(1f, 1200f / max(info.size.width * region.width, info.size.height * region.height))
        val width = max(1, (info.size.width * ratio).roundToInt())
        val height = max(1, (info.size.height * ratio).roundToInt())
        decoder.setTargetSize(width, height)
        val l = (region.left * width).roundToInt().coerceIn(0, width - 1)
        val t = (region.top * height).roundToInt().coerceIn(0, height - 1)
        decoder.crop = android.graphics.Rect(l, t, (region.right * width).roundToInt().coerceIn(l + 1, width),
            (region.bottom * height).roundToInt().coerceIn(t + 1, height))
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
    val rotated = if (turns % 4 == 0) cropped else Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height,
        Matrix().apply { postRotate(turns * 90f) }, true)
    val edge = max(rotated.width, rotated.height)
    val output = if (edge <= 1200) rotated else Bitmap.createScaledBitmap(rotated,
        max(1, rotated.width * 1200 / edge), max(1, rotated.height * 1200 / edge), true)
    try {
        return ByteArrayOutputStream().use {
            output.compress(Bitmap.CompressFormat.JPEG, 85, it)
            Base64.encodeToString(it.toByteArray(), Base64.NO_WRAP)
        }
    } finally {
        if (output !== rotated) output.recycle()
        if (rotated !== cropped) rotated.recycle()
        cropped.recycle()
    }
}
