package com.godly.moonzoom

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.roundToInt

enum class Frame(val label: String, val ratio: Float) {
    R34("3:4", 3f / 4f),
    R11("1:1", 1f),
    R916("9:16", 9f / 16f),
    FULL("FULL", 0f)
}

private const val AI_FRAMES = 5

private suspend fun Context.awaitCameraProvider(): ProcessCameraProvider = suspendCoroutine { cont ->
    val f = ProcessCameraProvider.getInstance(this)
    f.addListener({ cont.resume(f.get()) }, ContextCompat.getMainExecutor(this))
}

private suspend fun ImageCapture.takeToFile(executor: Executor, file: File) =
    suspendCancellableCoroutine<Unit> { c ->
        takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    c.resume(Unit)
                }

                override fun onError(exception: ImageCaptureException) {
                    c.resumeWithException(exception)
                }
            }
        )
    }

/** Bọc AnimatedVisibility ở ngoài mọi ColumnScope/RowScope để không bị nhầm bản có receiver. */
@Composable
private fun FadeSlide(visible: Boolean, slide: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = if (slide) fadeIn(tween(160)) + slideInVertically(tween(180)) { it / 4 } else fadeIn(tween(140)),
        exit = if (slide) fadeOut(tween(120)) + slideOutVertically(tween(140)) { it / 4 } else fadeOut(tween(100))
    ) { content() }
}

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            CameraContent()
        } else {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Moon Zoom cần quyền Camera để hoạt động.", color = Color.White, textAlign = TextAlign.Center)
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Cấp quyền") }
                Button(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.fromParts("package", context.packageName, null))
                    )
                }) { Text("Mở cài đặt ứng dụng") }
            }
        }
    }
}

@Composable
private fun CameraContent() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    val prefs = remember { context.getSharedPreferences("moonzoom", Context.MODE_PRIVATE) }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE // TextureView: cho phép phóng số
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val applier = remember { ZoomApplier(previewView) }
    val controller = remember { SmoothZoomController(scope) { z, fin -> applier.apply(z, fin) } }
    val zoomUi = remember { ZoomUi() }
    ZoomAutoCollapse(zoomUi)

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var focusTick by remember { mutableIntStateOf(0) }
    var expIndex by remember { mutableFloatStateOf(0f) }
    var message by remember { mutableStateOf<String?>(null) }
    var lastUri by remember { mutableStateOf<Uri?>(null) }
    var thumb by remember { mutableStateOf<ImageBitmap?>(null) }
    var fx by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<Session>(Session.Idle) }
    var job by remember { mutableStateOf<Job?>(null) }
    var aiOn by remember { mutableStateOf(prefs.getBoolean("ai", false)) }
    var wmOn by remember { mutableStateOf(prefs.getBoolean("wm", false)) }
    var frame by remember { mutableStateOf(Frame.values()[prefs.getInt("frame", 0).coerceIn(0, 3)]) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    val cameraNow by rememberUpdatedState(camera)
    val fullRatio = if (boxSize.height > 0) boxSize.width.toFloat() / boxSize.height else 9f / 20f
    fun ratioOf(f: Frame): Float = if (f == Frame.FULL) fullRatio else f.ratio

    LaunchedEffect(Unit) {
        GalleryHelper.latest(context)?.let { u ->
            lastUri = u
            thumb = GalleryHelper.thumb(context, u)?.asImageBitmap()
        }
    }

    LaunchedEffect(message) {
        message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); message = null }
    }

    LaunchedEffect(lensFacing) {
        try {
            val provider = context.awaitCameraProvider()
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            if (!provider.hasCamera(selector)) {
                message = "Thiết bị không có camera này"
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                    CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                return@LaunchedEffect
            }
            val preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .build()
            provider.unbindAll()
            val cam = provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
            imageCapture = capture
            camera = cam
            flashMode = ImageCapture.FLASH_MODE_OFF
            expIndex = 0f
            focusPoint = null
            applier.camera = cam
            applier.nativeMax = (cam.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f).coerceAtLeast(1f)
            applier.reset()
            controller.setTarget(1f, immediate = true)
        } catch (e: Exception) {
            message = "Không mở được camera: ${e.message}"
        }
    }

    LaunchedEffect(flashMode, imageCapture) { imageCapture?.flashMode = flashMode }
    LaunchedEffect(focusTick) {
        if (focusTick > 0) {
            delay(4500); focusPoint = null
        }
    }

    val fxAlpha = animateFloatAsState(if (fx) 0.8f else 0f, tween(110), label = "fx")

    fun shoot() {
        val ic = imageCapture ?: return
        if (busy) return
        busy = true
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch { fx = true; delay(90); fx = false }
        val extra = applier.extra
        val ratio = ratioOf(frame)
        val ai = aiOn
        val wm = wmOn
        job = scope.launch {
            val temps = mutableListOf<File>()
            try {
                val uri: Uri
                if (!ai) {
                    val f = File.createTempFile("cap_", ".jpg", context.cacheDir)
                    temps += f
                    ic.takeToFile(mainExecutor, f)
                    uri = CaptureSaver.saveNormal(context, f, extra, ratio, wm) { p, s ->
                        if (wm) session = Session.Processing(p, s)
                    }
                } else {
                    ic.flashMode = ImageCapture.FLASH_MODE_OFF
                    val files = ArrayList<File>()
                    for (i in 0 until AI_FRAMES) {
                        session = Session.Capturing(i, AI_FRAMES)
                        val f = File.createTempFile("ai_", ".jpg", context.cacheDir)
                        temps += f
                        ic.takeToFile(mainExecutor, f)
                        files += f
                    }
                    session = Session.Processing(0f, "Đang đọc các khung")
                    val res = MoonAIEnhancer.process(context, files, extra, ratio) { p, s ->
                        session = Session.Processing(p * 0.9f, s)
                    }
                    val meta = ImageMeta.read(files[0])
                    uri = CaptureSaver.finishBitmap(
                        context, res.bitmap, meta, extra, wm,
                        if (extra > 1.01f) "AI_digital" else "AI", upscale = false
                    ) { p, s -> session = Session.Processing(0.9f + p * 0.1f, s) }
                }
                lastUri = uri
                thumb = GalleryHelper.thumb(context, uri)?.asImageBitmap()
            } catch (e: CancellationException) {
                message = "Đã hủy"
                throw e
            } catch (e: OutOfMemoryError) {
                message = "Hết bộ nhớ khi xử lý ảnh"
            } catch (e: Exception) {
                message = "Lỗi chụp: ${e.message}"
            } finally {
                temps.forEach { it.delete() }
                ic.flashMode = flashMode
                session = Session.Idle
                busy = false
            }
        }
    }

    val hasFlash = camera?.cameraInfo?.hasFlashUnit() == true

    Box(Modifier.fillMaxSize().onSizeChanged { boxSize = it }) {
        // ---------------- Preview ----------------
        val previewMod = if (frame == Frame.FULL) Modifier.fillMaxSize()
        else Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 100.dp)
            .fillMaxWidth().aspectRatio(frame.ratio)
        Box(previewMod.clipToBounds()) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            Box(
                Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { off ->
                            val cam = cameraNow ?: return@detectTapGestures
                            val ex = previewView.scaleX
                            val cx = previewView.width / 2f
                            val cy = previewView.height / 2f
                            val x = cx + (off.x - cx) / ex
                            val y = cy + (off.y - cy) / ex
                            val point = previewView.meteringPointFactory.createPoint(x, y)
                            val action = FocusMeteringAction.Builder(
                                point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                            ).setAutoCancelDuration(5, TimeUnit.SECONDS).build()
                            cam.cameraControl.startFocusAndMetering(action)
                            focusPoint = off
                            focusTick++
                        }
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoomChange, _ ->
                            if (zoomChange != 1f) {
                                controller.setTarget(controller.target * zoomChange)
                                zoomUi.touch()
                            }
                        }
                    }
            )

            focusPoint?.let { p ->
                val ring = remember { Animatable(1.5f) }
                LaunchedEffect(focusTick) { ring.snapTo(1.5f); ring.animateTo(1f, tween(260)) }
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Color.White, 34.dp.toPx() * ring.value, p, style = Stroke(2.dp.toPx()))
                    drawCircle(Color.White.copy(0.9f), 3.dp.toPx(), p)
                }
                val expState = camera?.cameraInfo?.exposureState
                val range = expState?.exposureCompensationRange
                if (expState != null && range != null && expState.isExposureCompensationSupported && range.upper > range.lower) {
                    val sliderW = 160.dp
                    Column(
                        Modifier.offset {
                            val maxX = (previewView.width - sliderW.toPx()).toInt().coerceAtLeast(0)
                            val x = (p.x - sliderW.toPx() / 2f).roundToInt().coerceIn(0, maxX)
                            val y = if (p.y > previewView.height * 0.8f) p.y - 96.dp.toPx() else p.y + 44.dp.toPx()
                            IntOffset(x, y.roundToInt())
                        }.width(sliderW),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Slider(
                            value = expIndex,
                            onValueChange = {
                                expIndex = it
                                cameraNow?.cameraControl?.setExposureCompensationIndex(it.roundToInt())
                                focusTick++
                            },
                            valueRange = range.lower.toFloat()..range.upper.toFloat(),
                            steps = (range.upper - range.lower - 1).coerceAtLeast(0),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = MoonBlue,
                                inactiveTrackColor = Color.White.copy(0.3f)
                            )
                        )
                        Text(
                            "EV " + String.format(
                                java.util.Locale.US, "%+.1f",
                                expIndex.roundToInt() * expState.exposureCompensationStep.toFloat()
                            ),
                            color = Color.White, fontSize = 11.sp
                        )
                    }
                }
            }
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = fxAlpha.value)))
        }

        // ---------------- Thanh trên ----------------
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Moon Zoom", color = Color.White.copy(0.92f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("By GODLY", color = Color.White.copy(0.6f), fontSize = 10.sp)
                }
                if (hasFlash) {
                    GlassButton(Modifier.height(34.dp).width(92.dp), selected = flashMode != ImageCapture.FLASH_MODE_OFF, onClick = {
                        flashMode = when (flashMode) {
                            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                            else -> ImageCapture.FLASH_MODE_OFF
                        }
                    }) {
                        GlassLabel(
                            when (flashMode) {
                                ImageCapture.FLASH_MODE_ON -> "FLASH ON"
                                ImageCapture.FLASH_MODE_AUTO -> "FLASH AUTO"
                                else -> "FLASH OFF"
                            },
                            flashMode != ImageCapture.FLASH_MODE_OFF, 11
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (f in Frame.values()) {
                        GlassButton(Modifier.width(56.dp).height(32.dp), selected = frame == f, onClick = {
                            frame = f
                            prefs.edit().putInt("frame", f.ordinal).apply()
                        }) { GlassLabel(f.label, frame == f, 11) }
                    }
                }
            }
        }

        // ---------------- Điều khiển dưới ----------------
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.fillMaxWidth().height(124.dp), contentAlignment = Alignment.BottomCenter) {
                FadeSlide(visible = !zoomUi.expanded, slide = false) { ZoomChips(controller, zoomUi) }
                FadeSlide(visible = zoomUi.expanded, slide = true) { ZoomArcDial(controller, zoomUi) }
            }
            Box(Modifier.liquidGlass(tint = ZoomYellow, strength = 1.2f).padding(horizontal = 18.dp, vertical = 5.dp)) {
                GlassLabel("ZOOM", true, 12)
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassButton(Modifier.size(52.dp), corner = 14.dp, enabled = lastUri != null, onClick = {
                    lastUri?.let {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW).setDataAndType(it, "image/jpeg")
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            )
                        }
                    }
                }) {
                    val t = thumb
                    if (t != null) {
                        Image(
                            t, contentDescription = "Ảnh mới nhất", contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().padding(3.dp).clip(RoundedCornerShape(11.dp))
                        )
                    }
                }
                GlassButton(Modifier.size(52.dp), selected = aiOn, onClick = {
                    aiOn = !aiOn
                    prefs.edit().putBoolean("ai", aiOn).apply()
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }) { GlassLabel("AI", aiOn, 15) }
                ShutterButton(enabled = !busy && camera != null, onClick = { shoot() })
                GlassButton(Modifier.size(52.dp), selected = wmOn, onClick = {
                    wmOn = !wmOn
                    prefs.edit().putBoolean("wm", wmOn).apply()
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }) { GlassLabel("WM", wmOn, 13) }
                GlassButton(Modifier.size(52.dp), onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                        CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                }) { GlassLabel("ĐỔI", false, 12) }
            }
        }

        SessionOverlay(session) { job?.cancel() }
    }
}
