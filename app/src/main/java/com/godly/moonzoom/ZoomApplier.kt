package com.godly.moonzoom

import android.os.SystemClock
import androidx.camera.core.Camera
import androidx.camera.view.PreviewView
import kotlin.math.abs
import kotlin.math.min

/**
 * Áp zoom lên camera: phần đầu do CameraX/camera xử lý (setZoomRatio, giới hạn tần suất ~35Hz để không
 * làm nghẹt pipeline), phần vượt quá mức tối đa của camera là phóng/crop số thật trên preview (GPU).
 */
class ZoomApplier(private val previewView: PreviewView) {
    var camera: Camera? = null
    var nativeMax: Float = 1f

    private var lastNative = 1f
    private var lastApplyNs = 0L

    /** Hệ số crop số hiện tại (>= 1). Dùng để crop ảnh chụp cho khớp preview. */
    var extra: Float = 1f
        private set

    fun reset() {
        lastNative = 1f
        lastApplyNs = 0L
        extra = 1f
        previewView.scaleX = 1f
        previewView.scaleY = 1f
    }

    fun apply(zoom: Float, done: Boolean) {
        val native = min(zoom, nativeMax).coerceAtLeast(1f)
        val now = SystemClock.elapsedRealtimeNanos()
        val cam = camera
        if (cam != null) {
            val changed = abs(native - lastNative) / lastNative > 0.003f
            if (done && abs(native - lastNative) > 1e-4f || (changed && now - lastApplyNs >= 28_000_000L)) {
                cam.cameraControl.setZoomRatio(native)
                lastNative = native
                lastApplyNs = now
            }
        }
        extra = (zoom / lastNative).coerceAtLeast(1f)
        previewView.scaleX = extra
        previewView.scaleY = extra
    }
}
