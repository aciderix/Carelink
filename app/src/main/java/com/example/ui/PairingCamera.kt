package com.example.ui

import android.graphics.Bitmap
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

fun pairingBitmap(code: String): Bitmap {
    val matrix = MultiFormatWriter().encode(code, BarcodeFormat.QR_CODE, 720, 720,
        mapOf(EncodeHintType.MARGIN to 2, EncodeHintType.CHARACTER_SET to "UTF-8"))
    return Bitmap.createBitmap(720, 720, Bitmap.Config.ARGB_8888).apply {
        val pixels = IntArray(720 * 720) { i -> if (matrix[i % 720, i / 720]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
        setPixels(pixels, 0, 720, 0, 0, 720, 720)
    }
}

@Composable
fun PairingCamera(modifier: Modifier = Modifier, onCode: (String) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val preview = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val callback by rememberUpdatedState(onCode)
    val error by rememberUpdatedState(onError)
    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(context)
        var disposed = false
        var provider: ProcessCameraProvider? = null
        var found = false
        future.addListener({
            if (!disposed) {
                runCatching {
                    provider = future.get()
                    val view = Preview.Builder().build().apply { surfaceProvider = preview.surfaceProvider }
                    val analyzer = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    analyzer.setAnalyzer(executor) { image ->
                        try {
                            if (!found) {
                                val plane = image.planes[0]; val buffer = plane.buffer; val bytes = ByteArray(image.width * image.height)
                                for (y in 0 until image.height) for (x in 0 until image.width)
                                    bytes[y * image.width + x] = buffer.get(y * plane.rowStride + x * plane.pixelStride)
                                val source = PlanarYUVLuminanceSource(bytes, image.width, image.height, 0, 0, image.width, image.height, false)
                                val code = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source))).text
                                if (code.startsWith("CL2.")) { found = true; ContextCompat.getMainExecutor(context).execute { callback(code) } }
                            }
                        } catch (_: NotFoundException) { }
                        catch (_: Exception) { }
                        finally { image.close() }
                    }
                    provider!!.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, view, analyzer)
                }.onFailure { error("Caméra indisponible. Collez le code à la place.") }
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose { disposed = true; provider?.unbindAll(); executor.shutdown() }
    }
    AndroidView(factory = { preview }, modifier = modifier)
}
