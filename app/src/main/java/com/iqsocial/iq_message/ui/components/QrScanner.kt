package com.iqsocial.iq_message.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.IqTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The camera, full screen, until it sees a QR code; then [onFound] with what the
 * code says, once.
 *
 * The app's own scanner rather than Google's code scanner. That one is a module
 * Play services downloads on first use, and where the download fails - no Google
 * account on the phone, no Play services - it never opened, with nothing said.
 * This needs only the camera, which video calls already ask for, and ZXing, which
 * already draws this app's own code.
 *
 * Shared by the QR screen and the rewards special-person picker.
 */
@Composable
fun QrScanDialog(onFound: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val d = IqTheme.dimens
        val context = LocalContext.current
        var allowed by remember { mutableStateOf(hasCamera(context)) }
        var refused by remember { mutableStateOf(false) }
        var problem by remember { mutableStateOf<String?>(null) }
        val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            allowed = granted
            refused = !granted
        }
        LaunchedEffect(Unit) {
            if (!allowed) ask.launch(Manifest.permission.CAMERA)
        }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (allowed) {
                CameraFrames(onFound = onFound, onProblem = { problem = it })
            }

            // The text sits on dark bands: the camera's picture can be white, and white
            // on white hid the close button.
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(SCRIM)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(start = d.gapXs, end = d.gutter, top = d.gapSm, bottom = d.gapSm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircleIconButton(
                        icon = IqIcons.Close,
                        contentDescription = "Close",
                        onClick = onDismiss,
                        tint = Color.White
                    )
                    Spacer(Modifier.width(d.gapXs))
                    Text(
                        "Scan a QR code",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                }

                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    // Where to hold the code. Only a guide: the whole frame is read.
                    Box(
                        Modifier
                            .fillMaxWidth(0.7f)
                            .aspectRatio(1f)
                            .border(3.dp, Color.White, RoundedCornerShape(d.radiusXl))
                    )
                }

                val message = when {
                    refused -> "Allow the camera to scan a QR code."
                    problem != null -> problem
                    else -> "Point the camera at an IQ QR code."
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(SCRIM)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(top = d.gapMd, bottom = d.gapXl)
                ) {
                    Text(
                        message.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = d.gutter)
                    )
                    if (refused) {
                        Spacer(Modifier.height(d.gapMd))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            Text(
                                "Open settings",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(d.radiusMd))
                                    .background(Crimson)
                                    .clickable { openAppSettings(context) }
                                    .padding(horizontal = d.gapLg, vertical = d.gapSm)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The back camera's preview, with every frame handed to ZXing until one has a code. */
@Composable
private fun CameraFrames(onFound: (String) -> Unit, onProblem: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val found by rememberUpdatedState(onFound)
    // A TextureView, not the default SurfaceView: that one draws behind its window,
    // and in this dialog the picture stayed under the black backdrop.
    val preview = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(lifecycleOwner) {
        val provider = try {
            cameraProvider(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onProblem("The camera could not start.")
            return@LaunchedEffect
        }
        val shown = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        val reading = Executors.newSingleThreadExecutor()
        val main = ContextCompat.getMainExecutor(context)
        val done = AtomicBoolean(false)
        analysis.setAnalyzer(reading) { frame ->
            val text = frame.use { if (done.get()) null else readFrame(it) }
            if (text != null && done.compareAndSet(false, true)) main.execute { found(text) }
        }
        val camera = when {
            provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
            provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
            else -> null
        }
        try {
            if (camera == null) {
                onProblem("This phone has no camera to scan with.")
                return@LaunchedEffect
            }
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, camera, shown, analysis)
            awaitCancellation()
        } catch (e: IllegalStateException) {
            onProblem("The camera could not start.")
        } catch (e: IllegalArgumentException) {
            onProblem("The camera could not start.")
        } finally {
            provider.unbind(shown, analysis)
            reading.shutdown()
        }
    }

    AndroidView(factory = { preview }, modifier = Modifier.fillMaxSize())
}

private suspend fun cameraProvider(context: Context): ProcessCameraProvider =
    suspendCancellableCoroutine { waiting ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            { runCatching { future.get() }.fold(waiting::resume, waiting::resumeWithException) },
            ContextCompat.getMainExecutor(context)
        )
    }

/** The brightness plane of a camera frame, which is all ZXing reads. */
private fun readFrame(frame: ImageProxy): String? {
    val plane = frame.planes[0]
    val width = frame.width
    val height = frame.height
    val buffer = plane.buffer
    val stride = plane.rowStride
    val luminance = ByteArray(width * height)
    for (row in 0 until height) {
        buffer.position(row * stride)
        buffer.get(luminance, row * width, width)
    }
    return decodeQr(luminance, width, height)
}

/**
 * The text of the QR code in a [width] × [height] brightness image, one byte a
 * pixel; null when there is none. Any rotation reads, so the frame is not turned
 * upright first.
 */
fun decodeQr(luminance: ByteArray, width: Int, height: Int): String? {
    val source = PlanarYUVLuminanceSource(luminance, width, height, 0, 0, width, height, false)
    return try {
        QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), HINTS).text
    } catch (_: ReaderException) {
        null
    }
}

private val HINTS = mapOf(DecodeHintType.TRY_HARDER to true)

private val SCRIM = Color.Black.copy(alpha = 0.55f)

private fun hasCamera(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
