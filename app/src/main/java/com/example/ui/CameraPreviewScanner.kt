package com.example.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.countryatv.core.LabelPayload
import com.countryatv.core.Vin
import com.countryatv.core.VinCheck
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// The camera picture with the barcode reader. The Classic screens use the first three parameters;
// the rebuilt Scan screen also uses the rest (scan-pin-ui-spec.md 2e):
// - [paused]: a read is showing. The picture freezes and nothing more is reported, including a
//   frame that was still decoding when the pause began (checked on the main thread).
// - [ignoreUntilGone]: the value just read is ignored after Scan another until it has been out of
//   view for about 1 s, so the same tag isn't read again at once.
// - [onePerFrame]: with several codes in view take one, preferring our QR tag, then a valid VIN, then
//   anything else, nearest the centre.
// - [torchOn] asks for the light; [onTorch] reports whether the camera has one and its real state.
@SuppressLint("UnsafeOptInUsageError")
@Composable
fun CameraPreviewScanner(
    onBarcodeDetected: (String) -> Unit,
    onFailure: () -> Unit = {},
    modifier: Modifier = Modifier,
    paused: Boolean = false,
    ignoreUntilGone: String? = null,
    onePerFrame: Boolean = false,
    torchOn: Boolean = false,
    onTorch: (available: Boolean, on: Boolean) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    // Throttle detections to avoid rapid double scans
    var lastScannedValue by remember { mutableStateOf("") }
    var lastScanTime by remember { mutableLongStateOf(0L) }

    val isPaused by rememberUpdatedState(paused)
    val ignore by rememberUpdatedState(ignoreUntilGone)
    val report by rememberUpdatedState(onBarcodeDetected)
    val torchReport by rememberUpdatedState(onTorch)
    var ignoreSeenAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(ignoreUntilGone) { ignoreSeenAt = System.currentTimeMillis() }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var frozen by remember { mutableStateOf<Bitmap?>(null) }

    // Freeze: lay the last picture over the preview while a read shows; the preview stays bound so
    // resuming is instant.
    LaunchedEffect(paused) { frozen = if (paused) previewView?.bitmap else null }
    LaunchedEffect(torchOn, camera) {
        val c = camera ?: return@LaunchedEffect
        if (c.cameraInfo.hasFlashUnit()) c.cameraControl.enableTorch(torchOn)
    }

    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val view = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                previewView = view

                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = view.surfaceProvider
                        }

                        // Configure ML Kit Barcode Scanner
                        val options = BarcodeScannerOptions.Builder()
                            // Our own QR tags plus the symbologies OEM labels and boxes use (VIN labels are Code 39).
                            // Fewer formats means fewer misreads than FORMAT_ALL_FORMATS.
                            .setBarcodeFormats(
                                // Our QR labels, plus the codes VIN stickers use.
                                Barcode.FORMAT_QR_CODE,
                                Barcode.FORMAT_DATA_MATRIX,
                                Barcode.FORMAT_CODE_128,
                                Barcode.FORMAT_CODE_39
                            )
                            .build()
                        val scanner = BarcodeScanning.getClient(options)

                        val imageAnalyzer = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { analysis ->
                                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                    val mediaImage = imageProxy.image
                                    if (mediaImage != null) {
                                        val rotation = imageProxy.imageInfo.rotationDegrees
                                        val w = if (rotation % 180 == 0) imageProxy.width else imageProxy.height
                                        val h = if (rotation % 180 == 0) imageProxy.height else imageProxy.width
                                        val image = InputImage.fromMediaImage(mediaImage, rotation)
                                        scanner.process(image)
                                            .addOnSuccessListener { barcodes ->
                                                // Runs on the main thread: a frame finishing after the pause is dropped here.
                                                if (isPaused) return@addOnSuccessListener
                                                val now = System.currentTimeMillis()
                                                var found = barcodes.filter { !it.rawValue.isNullOrBlank() }
                                                val skip = ignore
                                                if (skip != null && found.any { it.rawValue == skip }) {
                                                    val gone = now - ignoreSeenAt > 1000
                                                    ignoreSeenAt = now
                                                    if (!gone) found = found.filter { it.rawValue != skip }
                                                }
                                                if (onePerFrame) found = listOfNotNull(pick(found, w / 2f, h / 2f))
                                                for (barcode in found) {
                                                    val rawValue = barcode.rawValue ?: continue
                                                    if (rawValue != lastScannedValue || now - lastScanTime > 2500L) {
                                                        lastScannedValue = rawValue
                                                        lastScanTime = now
                                                        report(rawValue)
                                                    }
                                                }
                                            }
                                            .addOnFailureListener {
                                                // A single unreadable frame is normal; the next frame is tried automatically.
                                                android.util.Log.w("CameraPreviewScanner", "Frame could not be decoded", it)
                                            }
                                            .addOnCompleteListener {
                                                imageProxy.close()
                                            }
                                    } else {
                                        imageProxy.close()
                                    }
                                }
                            }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        cameraProvider.unbindAll()
                        val bound = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalyzer
                        )
                        camera = bound
                        val hasTorch = bound.cameraInfo.hasFlashUnit()
                        torchReport(hasTorch, false)
                        // The Light button follows the camera's real torch state; it goes off when the camera closes.
                        bound.cameraInfo.torchState.observe(lifecycleOwner) { state -> torchReport(hasTorch, state == TorchState.ON) }
                    } catch (exc: Exception) {
                        android.util.Log.e("CameraPreviewScanner", "Camera failed to start", exc)
                        onFailure()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                view
            },
            modifier = Modifier.fillMaxSize()
        )
        frozen?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

// Our QR tag first, then a valid VIN, then anything else; nearest the centre within each.
private fun pick(found: List<Barcode>, cx: Float, cy: Float): Barcode? {
    fun rank(b: Barcode): Int {
        val v = b.rawValue.orEmpty().trim()
        return when {
            LabelPayload.decode(v) != null -> 0
            Vin.normalize(v).length == 17 && Vin.check(v) == VinCheck.Ok -> 1
            else -> 2
        }
    }
    fun dist(b: Barcode): Float {
        val r = b.boundingBox ?: return Float.MAX_VALUE
        val dx = r.exactCenterX() - cx; val dy = r.exactCenterY() - cy
        return dx * dx + dy * dy
    }
    return found.minWithOrNull(compareBy<Barcode>({ rank(it) }, { dist(it) }))
}
