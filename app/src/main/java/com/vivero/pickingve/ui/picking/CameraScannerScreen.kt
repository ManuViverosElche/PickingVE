package com.vivero.pickingve.ui.picking

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.YuvImage
import android.media.Image
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.CameraUnavailableException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.vivero.pickingve.domain.usecase.ScanDebouncer
import com.vivero.pickingve.scanner.BarcodeAnalyzer
import com.vivero.pickingve.scanner.OcrReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraScannerScreen(
    viewModel: PickingViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = context as? LifecycleOwner
    val debouncer = remember { ScanDebouncer() }
    val executor = remember { Executors.newSingleThreadExecutor() }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var ocrLoading by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        permissionDenied = !granted
    }

    LaunchedEffect(permissionGranted) {
        if (!permissionGranted && !permissionDenied) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (permissionDenied) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Text(
                text = "Permiso de cámara denegado.",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Actívalo en Ajustes del sistema para poder escanear etiquetas.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Button(onClick = onClose) { Text("Volver") }
        }
        return
    }

    if (cameraError != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Text(
                text = "No se pudo abrir la cámara.",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = cameraError.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Button(onClick = onClose) { Text("Volver") }
        }
        return
    }

    if (!permissionGranted) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text(
                    text = "Solicitando permiso de cámara…",
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        return
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                try {
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val capture = ImageCapture.Builder().build()
                    imageCapture = capture
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { it.setAnalyzer(executor, BarcodeAnalyzer(debouncer, viewModel::onBarcodeScanned)) }

                    provider.unbindAll()
                    val owner = lifecycleOwner
                    if (owner == null) {
                        cameraError = "Contexto de ciclo de vida no disponible"
                        return@addListener
                    }
                    provider.bindToLifecycle(
                        owner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture,
                        analysis
                    )
                } catch (e: CameraUnavailableException) {
                    cameraError = "No hay cámara disponible en este dispositivo"
                } catch (e: Exception) {
                    cameraError = e.message ?: "Error desconocido al abrir la cámara"
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Enfoca el código EAN de la etiqueta",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Cerrar cámara")
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Button(
                onClick = {
                    ocrLoading = true
                    runOcr(imageCapture, viewModel) { ocrLoading = false }
                },
                enabled = !ocrLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (ocrLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Filled.DocumentScanner, contentDescription = null)
                }
                Text(" Sin EAN? Capturar etiqueta por OCR")
            }
        }
    }
}

private fun runOcr(
    imageCapture: ImageCapture?,
    viewModel: PickingViewModel,
    onDone: () -> Unit
) {
    val capture = imageCapture ?: run { onDone(); return }
    CoroutineScope(Dispatchers.Main).launch {
        try {
            val bitmap = capture.takePictureBitmap()
            val text = withContext(Dispatchers.Default) { bitmap?.let { OcrReader.readText(it) } }
            if (text != null) {
                viewModel.onOcrText(text)
            } else {
                viewModel.showOcrError()
            }
        } catch (e: Exception) {
            viewModel.showOcrError()
        }
        onDone()
    }
}

private suspend fun ImageCapture.takePictureBitmap(): Bitmap? = suspendCoroutine { cont ->
    takePicture(
        Executors.newSingleThreadExecutor(),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = image.toBitmapCompat()
                image.close()
                cont.resume(bitmap)
            }

            override fun onError(exception: ImageCaptureException) {
                cont.resume(null)
            }
        }
    )
}

/** Converts a YUV_420_888 ImageProxy to an ARGB Bitmap. */
private fun ImageProxy.toBitmapCompat(): Bitmap {
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer
    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 95, out)
    return BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
}

