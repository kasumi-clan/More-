package com.godly.moonzoom

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

fun formatZoom(z: Float): String = when {
    z >= 99.95f -> "100x"
    abs(z - z.roundToInt()) < 0.05f -> "${z.roundToInt()}x"
    z < 10f -> String.format(Locale.US, "%.1fx", z)
    else -> String.format(Locale.US, "%.0fx", z)
}

/** Trạng thái hiện/ẩn của vòng zoom. Chỉ `expanded` là state; thời điểm chạm lưu thường để không recomposition. */
class ZoomUi {
    var expanded by mutableStateOf(false)
    var lastTouch: Long = 0L

    fun touch() {
        lastTouch = SystemClock.elapsedRealtime()
        if (!expanded) expanded = true
    }
}

private const val SPAN_DEG = 300f
private val MAJORS = listOf(1f, 2f, 3f, 5f, 10f, 20f, 30f, 50f, 100f)

private class Tick(val frac: Float, val level: Int)

private fun buildTicks(): List<Tick> {
    val logMax = ln(SmoothZoomController.MAX)
    val out = ArrayList<Tick>()
    fun add(v: Float) {
        val major = MAJORS.any { abs(it - v) < 0.001f }
        val medium = !major && (abs(v * 2f - (v * 2f).roundToInt()) < 0.001f)
        out.add(Tick(ln(v) / logMax, if (major) 2 else if (medium) 1 else 0))
    }
    var v = 1f
    while (v < 3f - 0.001f) { add(v); v += 0.1f }
    v = 3f
    while (v < 10f - 0.001f) { add(v); v += 0.5f }
    v = 10f
    while (v < 20f - 0.001f) { add(v); v += 1f }
    v = 20f
    while (v < 50f - 0.001f) { add(v); v += 2f }
    v = 50f
    while (v < 100f - 0.001f) { add(v); v += 5f }
    add(100f)
    return out
}

private fun nearestPreset(z: Float): Int =
    SmoothZoomController.PRESETS.indexOfFirst { abs(ln(z / it)) < 0.02f }

/** Vòng thước zoom dạng cung tròn (như mẫu): kéo ngang để xoay, kim vàng cố định ở đỉnh, tới đúng 100x. */
@Composable
fun ZoomArcDial(controller: SmoothZoomController, ui: ZoomUi, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val haptic = LocalHapticFeedback.current
    val ticks = remember { buildTicks() }
    val logMax = remember { ln(SmoothZoomController.MAX) }
    val labelStyle = remember { TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
    val valueStyle = remember { TextStyle(color = ZoomYellow, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
    val labels = remember(textMeasurer) {
        MAJORS.associateWith { textMeasurer.measure(formatZoom(it).removeSuffix("x"), labelStyle) }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { nearestPreset(controller.value) }
            .distinctUntilChanged()
            .collect { if (it >= 0) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(124.dp)
            .clipToBounds()
            .pointerInput(Unit) {
                val pxPerFull = size.width * 0.55f * (SPAN_DEG * PI / 180.0).toFloat()
                detectHorizontalDragGestures(onDragStart = { ui.touch() }) { change, dx ->
                    change.consume()
                    val f = (ln(controller.target) / logMax - dx / pxPerFull).coerceIn(0f, 1f)
                    controller.setTarget(if (f >= 0.9999f) SmoothZoomController.MAX else exp(f * logMax))
                    ui.touch()
                }
            }
    ) {
        val w = size.width
        val R = w * 0.55f
        val topY = 16.dp.toPx()
        val cx = w / 2f
        val cy = topY + R
        val z = controller.value
        val frac = ln(z.coerceIn(1f, SmoothZoomController.MAX)) / logMax

        drawCircle(
            Brush.radialGradient(listOf(Color.Black.copy(0.40f), Color.Black.copy(0.62f)), Offset(cx, cy), R),
            radius = R, center = Offset(cx, cy)
        )
        drawCircle(Color.White.copy(0.16f), radius = R, center = Offset(cx, cy), style = Stroke(1.dp.toPx()))

        val rim = R - 3.dp.toPx()
        for (t in ticks) {
            val theta = (t.frac - frac) * SPAN_DEG
            if (abs(theta) > 66f) continue
            val rad = (theta * PI / 180.0).toFloat()
            val s = sin(rad)
            val c = cos(rad)
            val len = (if (t.level == 2) 17.dp else if (t.level == 1) 12.dp else 7.dp).toPx()
            val fade = 1f - (abs(theta) / 66f).pow(2.2f)
            val a = fade * (if (t.level == 2) 1f else 0.72f)
            drawLine(
                Color.White.copy(alpha = a),
                Offset(cx + rim * s, cy - rim * c),
                Offset(cx + (rim - len) * s, cy - (rim - len) * c),
                strokeWidth = (if (t.level == 2) 2.2.dp else 1.2.dp).toPx(),
                cap = StrokeCap.Round
            )
            if (t.level == 2) {
                val lab = labels[MAJORS.minByOrNull { abs(ln(it) / logMax - t.frac) }!!]
                if (lab != null) {
                    val lr = rim - len - 13.dp.toPx()
                    val p = Offset(cx + lr * s, cy - lr * c)
                    rotate(theta, pivot = p) {
                        drawText(
                            textLayoutResult = lab,
                            topLeft = Offset(p.x - lab.size.width / 2f, p.y - lab.size.height / 2f),
                            alpha = fade
                        )
                    }
                }
            }
        }

        // kim vàng ở đỉnh
        val tri = Path().apply {
            moveTo(cx - 6.dp.toPx(), topY + 2.dp.toPx())
            lineTo(cx + 6.dp.toPx(), topY + 2.dp.toPx())
            lineTo(cx, topY + 15.dp.toPx())
            close()
        }
        drawPath(tri, ZoomYellow)
        val vt = textMeasurer.measure(formatZoom(z), valueStyle)
        drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, topY + 36.dp.toPx()))
    }
}

@Composable
private fun ZoomChip(text: String, selected: Boolean, onClick: () -> Unit) {
    GlassButton(Modifier.width(56.dp).height(38.dp), selected = selected, onClick = onClick) {
        GlassLabel(text, selected = selected, fontSize = 14)
    }
}

/** Dạng gọn: chỉ có sẵn 1x và 5x. Kéo ngang trên hàng này thì bung ra thước zoom 100x. */
@Composable
fun ZoomChips(controller: SmoothZoomController, ui: ZoomUi, modifier: Modifier = Modifier) {
    val z = controller.value
    val items = ArrayList<Pair<Float, Boolean>>()   // (giá trị, là giá trị hiện tại động)
    items.add(1f to false)
    items.add(5f to false)
    if (abs(z - 1f) > 0.08f && abs(z - 5f) > 0.25f) {
        items.add(z to true)
        items.sortBy { it.first }
    }
    val logMax = ln(SmoothZoomController.MAX)
    Row(
        modifier
            .padding(vertical = 4.dp)
            .pointerInput(Unit) {
                val pxPerFull = size.width.coerceAtLeast(300) * 2.2f
                detectHorizontalDragGestures(onDragStart = { ui.touch() }) { change, dx ->
                    change.consume()
                    ui.touch()
                    val f = (ln(controller.target) / logMax - dx / pxPerFull).coerceIn(0f, 1f)
                    controller.setTarget(if (f >= 0.9999f) SmoothZoomController.MAX else exp(f * logMax))
                }
            },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for ((v, dynamic) in items) {
            val selected = dynamic || abs(z - v) / v < 0.05f
            ZoomChip(formatZoom(v), selected) { if (!dynamic) controller.setTarget(v) }
        }
    }
}

@Composable
fun ZoomAutoCollapse(ui: ZoomUi) {
    LaunchedEffect(ui.expanded) {
        while (ui.expanded) {
            delay(250)
            if (SystemClock.elapsedRealtime() - ui.lastTouch > 2200) ui.expanded = false
        }
    }
}
