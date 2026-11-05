package com.godly.moonzoom

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * Zoom mượt: nội suy theo thang logarit, đồng bộ theo khung hình (withFrameNanos) và theo thời gian thực
 * (dt) nên mượt như nhau ở 60/90/120Hz. Kẹp cứng 1x..100x. Không gây recomposition: UI chỉ đọc [value]
 * trong giai đoạn vẽ.
 * [onFrame] được gọi mỗi khung với (zoom hiện tại, đã đến đích chưa).
 */
class SmoothZoomController(
    private val scope: CoroutineScope,
    private val onFrame: (Float, Boolean) -> Unit
) {
    companion object {
        const val MIN = 1f
        const val MAX = 100f
        private const val TAU = 0.075f
        val PRESETS = listOf(1f, 2f, 5f, 10f, 30f, 50f, 100f)

        fun clamp(v: Float): Float = if (v.isNaN()) MIN else v.coerceIn(MIN, MAX)
    }

    var value by mutableFloatStateOf(MIN)
        private set

    var target: Float = MIN
        private set

    private var job: Job? = null

    fun setTarget(v: Float, immediate: Boolean = false) {
        target = clamp(v)
        if (immediate) {
            job?.cancel()
            value = target
            onFrame(value, true)
        } else {
            ensureLoop()
        }
    }

    private fun ensureLoop() {
        if (job?.isActive == true) return
        job = scope.launch {
            var last = withFrameNanos { it }
            while (isActive) {
                val now = withFrameNanos { it }
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                last = now
                val cur = value
                val t = target
                val d = ln(t / cur)
                if (abs(d) < 0.0008f) {
                    value = t
                    onFrame(t, true)
                    break
                }
                val a = 1f - exp(-dt / TAU)
                val next = clamp(cur * exp(d * a))
                value = next
                onFrame(next, false)
            }
        }
    }
}
