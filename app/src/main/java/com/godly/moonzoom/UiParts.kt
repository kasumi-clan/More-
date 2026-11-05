package com.godly.moonzoom

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

val MoonBlue = Color(0xFF8CC8FF)
val ZoomYellow = Color(0xFFFFD60A)
val ShutterAmber = Color(0xFFFFB000)

/**
 * Liquid Glass nhẹ: nền gradient trong suốt + vệt sáng phía trên + viền phản chiếu. Không dùng blur
 * thời gian thực nên không tốn GPU và không gây giật; brush được cache theo kích thước.
 */
fun Modifier.liquidGlass(
    corner: Dp = 999.dp,
    tint: Color = Color.White,
    strength: Float = 1f,
    ring: Color? = null
): Modifier = this.drawWithCache {
    val r = min(corner.toPx(), min(size.width, size.height) / 2f)
    val cr = CornerRadius(r, r)
    val fill = Brush.verticalGradient(
        listOf(
            tint.copy(alpha = (0.26f * strength).coerceAtMost(0.6f)),
            tint.copy(alpha = (0.08f * strength).coerceAtMost(0.3f))
        )
    )
    val hl = Brush.verticalGradient(listOf(Color.White.copy(0.30f), Color.Transparent), endY = size.height * 0.5f)
    val edge = Brush.linearGradient(
        listOf(Color.White.copy(0.8f), Color.White.copy(0.10f), Color.White.copy(0.45f)),
        start = Offset.Zero, end = Offset(size.width, size.height)
    )
    val sw = 1.dp.toPx()
    val rw = 1.6.dp.toPx()
    onDrawBehind {
        drawRoundRect(brush = fill, cornerRadius = cr)
        drawRoundRect(brush = hl, cornerRadius = cr)
        drawRoundRect(brush = edge, cornerRadius = cr, style = Stroke(sw))
        if (ring != null) drawRoundRect(color = ring, cornerRadius = cr, style = Stroke(rw))
    }
}

/** Hiệu ứng nhún khi nhấn (lò xo), đọc trạng thái trong giai đoạn vẽ nên không recomposition cha. */
fun Modifier.pressScale(source: InteractionSource, pressedScale: Float = 0.9f): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val s = animateFloatAsState(if (pressed) pressedScale else 1f, spring(dampingRatio = 0.55f, stiffness = 650f), label = "press")
    graphicsLayer {
        scaleX = s.value
        scaleY = s.value
    }
}

@Composable
fun GlassButton(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    corner: Dp = 999.dp,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val src = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(src)
            .liquidGlass(
                corner = corner,
                tint = if (selected) ZoomYellow else Color.White,
                strength = if (selected) 1.5f else 1f,
                ring = if (selected) ZoomYellow.copy(alpha = 0.9f) else null
            )
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
fun GlassLabel(text: String, selected: Boolean = false, fontSize: Int = 12) {
    Text(
        text,
        color = if (selected) ZoomYellow else Color.White,
        fontSize = fontSize.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center
    )
}

/** Nút chụp theo mẫu: vòng ngoài vàng cam, khe đen, lõi trắng; nhấn thì lõi co lại kiểu lò xo. */
@Composable
fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val disc = animateFloatAsState(if (pressed) 0.84f else 1f, spring(dampingRatio = 0.5f, stiffness = 700f), label = "disc")
    val ring = animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.6f, stiffness = 700f), label = "ringS")
    Box(
        Modifier
            .size(84.dp)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val rs = ring.value
            drawCircle(Color(0xFF0A0A0A), radius = (r - 6.dp.toPx()) * rs)
            drawCircle(ShutterAmber, radius = (r - 3.dp.toPx()) * rs, style = Stroke(6.dp.toPx()))
            drawCircle(
                if (enabled) Color.White else Color.White.copy(alpha = 0.5f),
                radius = (r - 12.dp.toPx()) * disc.value
            )
        }
    }
}

/** Vòng tiến trình Liquid Glass: giá trị [progress] luôn là số liệu thật do pipeline báo về. */
@Composable
fun GlassProgressRing(progress: Float, centerText: String, subText: String, size: Dp = 210.dp) {
    val p = animateFloatAsState(progress.coerceIn(0f, 1f), tween(250), label = "ring")
    val inf = rememberInfiniteTransition(label = "spin")
    val phase = inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "phase")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val pv = p.value
            val r = this.size.minDimension / 2f
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(0.20f), Color.White.copy(0.04f)), center, r),
                radius = r
            )
            val inner = r * 0.84f
            val clip = Path().apply { addOval(Rect(center, inner)) }
            clipPath(clip) {
                val top = center.y + inner - 2f * inner * pv
                drawRect(MoonBlue.copy(0.22f), Offset(0f, top), Size(this.size.width, this.size.height - top))
                drawRect(MoonBlue.copy(0.40f), Offset(0f, top), Size(this.size.width, 3.dp.toPx()))
            }
            val stroke = 8.dp.toPx()
            val inset = stroke / 2f + 2.dp.toPx()
            val tl = Offset(inset, inset)
            val sz = Size(this.size.width - 2 * inset, this.size.height - 2 * inset)
            drawArc(Color.White.copy(0.15f), -90f, 360f, false, tl, sz, style = Stroke(stroke))
            drawArc(MoonBlue.copy(0.30f), -90f, 360f * pv, false, tl, sz, style = Stroke(stroke * 2.2f, cap = StrokeCap.Round))
            drawArc(MoonBlue, -90f, 360f * pv, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(Color.White.copy(0.55f), -90f + phase.value * 360f, 18f, false, tl, sz, style = Stroke(stroke * 0.5f, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(centerText, color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
            Text(subText, color = Color.White.copy(0.8f), fontSize = 12.sp)
        }
    }
}

sealed interface Session {
    object Idle : Session
    data class Capturing(val frames: Int, val total: Int) : Session
    data class Processing(val progress: Float, val status: String) : Session
}

@Composable
fun SessionOverlay(session: Session, onCancel: () -> Unit) {
    if (session is Session.Idle) return
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.62f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            when (session) {
                is Session.Capturing -> {
                    Text("AI ZOOM", color = ZoomYellow, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    GlassProgressRing(
                        session.frames.toFloat() / session.total,
                        "${session.frames}/${session.total}", "khung"
                    )
                    Text("Giữ yên máy để thu nhiều khung", color = Color.White, fontSize = 13.sp)
                }
                is Session.Processing -> {
                    GlassProgressRing(session.progress, "${(session.progress * 100).toInt()}%", "xử lý ảnh")
                    Text(session.status, color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
                Session.Idle -> Unit
            }
            GlassButton(Modifier.padding(top = 4.dp), onClick = onCancel) {
                Box(Modifier.padding(horizontal = 22.dp, vertical = 10.dp)) { GlassLabel("HỦY", fontSize = 13) }
            }
        }
    }
}
