package id.chairul.cardy

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private const val CARD_RATIO = 1.75f   // standard business card ≈ 90 x 55 mm (3.5 x 2 in)

/** Guide frame position inside a w x h view. Shared by the overlay and the crop so they always match. */
fun guideRect(w: Float, h: Float, vertical: Boolean): Rect {
    var bw: Float
    var bh: Float
    if (!vertical) {
        bw = w * 0.88f; bh = bw / CARD_RATIO
    } else {
        bh = h * 0.58f; bw = bh / CARD_RATIO
        if (bw > w * 0.88f) { bw = w * 0.88f; bh = bw * CARD_RATIO }
    }
    val top = (h - bh) / 2f - h * 0.05f
    return Rect(Offset((w - bw) / 2f, top), Size(bw, bh))
}

@Composable
fun CameraScreen(onCaptured: (File) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasPerm by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        hasPerm = ok
        if (!ok) { Toast.makeText(ctx, "Camera permission needed", Toast.LENGTH_SHORT).show(); onClose() }
    }
    LaunchedEffect(Unit) { if (!hasPerm) permLauncher.launch(Manifest.permission.CAMERA) }

    if (!hasPerm) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }

    val controller = remember {
        LifecycleCameraController(ctx).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
        }
    }
    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }

    var vertical by remember { mutableStateOf(false) }
    var torch by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { c ->
                PreviewView(c).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    this.controller = controller
                }
            },
            modifier = Modifier.fillMaxSize().onSizeChanged { viewSize = it },
        )

        // Dimmed overlay with a clear card-shaped window
        Canvas(Modifier.fillMaxSize()) {
            val r = guideRect(size.width, size.height, vertical)
            val corner = CornerRadius(18.dp.toPx())
            val hole = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                addRoundRect(RoundRect(r, corner))
            }
            drawPath(hole, Color.Black.copy(alpha = 0.6f))
            drawRoundRect(Color.White, r.topLeft, r.size, corner, style = Stroke(2.dp.toPx()))
            // Accent corners
            val len = 28.dp.toPx(); val sw = 5.dp.toPx(); val accent = Color(0xFF4CAF50)
            listOf(
                r.topLeft to Offset(1f, 1f), Offset(r.right, r.top) to Offset(-1f, 1f),
                Offset(r.left, r.bottom) to Offset(1f, -1f), Offset(r.right, r.bottom) to Offset(-1f, -1f),
            ).forEach { (p, d) ->
                drawLine(accent, p, Offset(p.x + d.x * len, p.y), sw)
                drawLine(accent, p, Offset(p.x, p.y + d.y * len), sw)
            }
        }

        // Hint
        Text(
            "Fit the card inside the frame\nHold steady, avoid glare",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 56.dp),
        )

        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)) {
            Icon(Icons.Outlined.Close, "Close", tint = Color.White)
        }

        // Bottom controls
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vertical = !vertical }) {
                Icon(Icons.Outlined.ScreenRotation, "Vertical card", tint = Color.White)
            }
            Box(
                Modifier.size(76.dp).clip(CircleShape).border(4.dp, Color.White, CircleShape).padding(6.dp)
                    .clip(CircleShape).background(if (busy) Color.Gray else Color.White)
                    .clickable(enabled = !busy && viewSize != IntSize.Zero) {
                        busy = true
                        val vs = viewSize; val vert = vertical
                        controller.takePicture(
                            ContextCompat.getMainExecutor(ctx),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    scope.launch {
                                        val file = try {
                                            withContext(Dispatchers.Default) { cropToGuide(ctx, image, vs, vert) }
                                        } catch (e: Exception) {
                                            null
                                        } finally {
                                            image.close()
                                        }
                                        busy = false
                                        if (file != null) onCaptured(file)
                                        else Toast.makeText(ctx, "Capture failed, try again", Toast.LENGTH_SHORT).show()
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    busy = false
                                    Toast.makeText(ctx, "Capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) { if (busy) CircularProgressIndicator(Modifier.size(28.dp)) }
            IconButton(onClick = { torch = !torch; controller.enableTorch(torch) }) {
                Icon(if (torch) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff, "Flash", tint = Color.White)
            }
        }
    }
}

/**
 * Crop the captured frame to exactly the area inside the guide box.
 * The controller binds a ViewPort matching the PreviewView, so the image's cropRect (after rotation)
 * corresponds to what was visible on screen; the guide box is then mapped proportionally.
 */
private fun cropToGuide(ctx: Context, image: ImageProxy, view: IntSize, vertical: Boolean): File {
    val full = image.toBitmap()
    val cr = image.cropRect
    var bmp = if (cr.width() > 0 && cr.height() > 0 && cr.right <= full.width && cr.bottom <= full.height &&
        (cr.width() != full.width || cr.height() != full.height)
    ) Bitmap.createBitmap(full, cr.left, cr.top, cr.width(), cr.height()) else full

    val rot = image.imageInfo.rotationDegrees
    if (rot != 0) {
        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot.toFloat()) }, true)
    }

    val g = guideRect(view.width.toFloat(), view.height.toFloat(), vertical)
    val sx = bmp.width / view.width.toFloat()
    val sy = bmp.height / view.height.toFloat()
    val margin = 0.03f * g.width   // small margin so card edges aren't clipped
    val l = ((g.left - margin) * sx).roundToInt().coerceIn(0, bmp.width - 1)
    val t = ((g.top - margin) * sy).roundToInt().coerceIn(0, bmp.height - 1)
    val r = ((g.right + margin) * sx).roundToInt().coerceIn(l + 1, bmp.width)
    val b = ((g.bottom + margin) * sy).roundToInt().coerceIn(t + 1, bmp.height)
    var out = Bitmap.createBitmap(bmp, l, t, r - l, b - t)

    // Cap size (keeps archive small, OCR still sharp)
    val maxSide = 2200
    val longest = maxOf(out.width, out.height)
    if (longest > maxSide) {
        val s = maxSide / longest.toFloat()
        out = Bitmap.createScaledBitmap(out, (out.width * s).roundToInt(), (out.height * s).roundToInt(), true)
    }

    val dir = File(ctx.cacheDir, "cards").apply { mkdirs() }
    val f = File(dir, "cap_${System.currentTimeMillis()}.jpg")
    f.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    return f
}
