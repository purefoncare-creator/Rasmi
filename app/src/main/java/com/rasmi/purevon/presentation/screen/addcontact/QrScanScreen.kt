package com.rasmi.purevon.presentation.screen.addcontact

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.rasmi.purevon.R
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ✅ الماسح الداخلي — شاشة مسح رمز جهة الاتصال (CameraX + ML Kit)
 *
 * يفك ترميز QR من الكاميرا الخلفية مباشرة داخل Rasmi، ويعيد النص الخام
 * للمُستدعي (الذي يحلله QrPayloadParser ويملأ نموذج إضافة جهة الاتصال).
 * فك الترميز عبر ML Kit Bundled: أدق وأسرع من ZXing في الإضاءة الضعيفة
 * والأكواد المائلة/المتضررة، ويعمل دون اتصال وعلى كل الأجهزة.
 */
@Composable
fun QrScanScreen(
    onDecoded: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            CameraPreviewWithAnalyzer(onDecoded = onDecoded)
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.qr_camera_permission),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        // الشريط العلوي: رجوع + عنوان
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 40.dp, start = 8.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = Color.White
            )
        }
        Text(
            text = stringResource(R.string.qr_scan_title),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 52.dp)
        )
    }
}

@Composable
private fun CameraPreviewWithAnalyzer(onDecoded: (String) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    var torchOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener({
                        try {
                            val provider = providerFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also {
                                    it.setAnalyzer(executor, QrFrameAnalyzer(onDecoded))
                                }
                            provider.unbindAll()
                            camera = provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("QrScan", "Camera bind failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            }
        )

        // مبدل الفلاش — يسهّل المسح في الإضاءة المنخفضة
        IconButton(
            onClick = {
                torchOn = !torchOn
                camera?.cameraControl?.enableTorch(torchOn)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        ) {
            Icon(
                imageVector = if (torchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

/**
 * محلل الإطارات عبر ML Kit: يمرر كل إطار كـ InputImage إلى ماسح الباركود،
 * ويتجاهل الإطارات أثناء معالجة إطار سابق، ويغلق كل إطار حتمًا،
 * ويسلّم النتيجة على الخيط الرئيسي (التنقل لا يعمل من خيط الخلفية).
 * مُقيّد على رموز QR + النماذج لأداء أسرع (KEEP_ONLY_LATEST يبقي الإطار الأحدث).
 */
private class QrFrameAnalyzer(private val onResult: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val scanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )
    private val busy = AtomicBoolean(false)
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var delivered = false

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(image: ImageProxy) {
        if (delivered || !busy.compareAndSet(false, true)) {
            image.close()
            return
        }
        val mediaImage = image.image
        if (mediaImage == null) {
            busy.set(false)
            image.close()
            return
        }
        val inputImage = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)

        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                val text = barcodes.firstOrNull()?.rawValue
                if (text != null && !delivered) {
                    delivered = true
                    mainHandler.post { onResult(text) }
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.w("QrScan", "ML Kit decode failed", e)
            }
            .addOnCompleteListener {
                busy.set(false)
                image.close()
            }
    }
}