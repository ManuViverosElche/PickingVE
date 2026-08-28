package com.vivero.pickingve.ui.inventario

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Camara del inventario (D-221): analisis EAN continuo mientras esta abierta;
 * OCR SOLO al pulsar el boton para ahorrar bateria. Reutiliza BarcodeAnalyzer,
 * OcrReader y ScanDebouncer de picking sin tocarlos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvScannerScreen(
    viewModel: InvViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = context as? LifecycleOwner
    val debouncer = remember { ScanDebouncer() }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val ocrScope = rememberCoroutineScope()
    DisposableEffect(executor) { onDispose { executor.shutdown() } }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var ocrLoading by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
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

    androidx.compose.runtime.LaunchedEffect(permissionGranted) {
        if (!permissionGranted && !permissionDenied) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    androidx.compose.runtime.LaunchedEffect(aviso) {
        if (aviso != null) {
            delay(3000)
            aviso = null
        }
    }

    fun lanzarOcr() {
        val capture = imageCapture
        if (capture == null) {
            aviso = "La camara aun no está lista"
            return
        }
        ocrLoading = true
        runOcr(ocrScope, context, capture, executor, viewModel) { ocrLoading = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pistolear") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cerrar")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    aviso?.let {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                it,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                    Button(
                        onClick = { lanzarOcr() },
                        enabled = !ocrLoading && imageCapture != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    ) {
                        if (ocrLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("  Leyendo etiqueta…")
                        } else {
                            Text("CAPTURAR ETIQUETA SIN EAN (OCR)")
                        }
                    }
                    Text(
                        "Enfoca el código EAN o pulsa para capturar la etiqueta sin EAN",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    ) { padding ->
        when {
            permissionDenied -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Permiso de cámara denegado.", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Actívalo en Ajustes del sistema para poder pistolear.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Button(onClick = onClose) { Text("Volver") }
            }

            cameraError != null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No se pudo abrir la cámara.", style = MaterialTheme.typography.titleMedium)
                Text(cameraError.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onClose) { Text("Volver") }
            }

            !permissionGranted -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text("Solicitando permiso de cámara…", modifier = Modifier.padding(top = 12.dp))
                }
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
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
                                val capture = ImageCapture.Builder()
                                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                    .build()
                                imageCapture = capture
                                val analysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                    .also {
                                        it.setAnalyzer(
                                            executor,
                                            BarcodeAnalyzer(debouncer) { codigo ->
                                                if (Regex("^\\d{8}$|^\\d{13}$").matches(codigo)) {
                                                    viewModel.onBarcodeScanned(codigo)
                                                } else {
                                                    aviso = "¿Etiqueta sin EAN? Pulsa CAPTURAR ETIQUETA SIN EAN"
                                                }
                                            }
                                        )
                                    }
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
                            } catch (e: Exception) {
                                cameraError = e.message ?: "Error desconocido al abrir la cámara"
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private fun runOcr(
    scope: CoroutineScope,
    context: android.content.Context,
    imageCapture: ImageCapture,
    executor: Executor,
    viewModel: InvViewModel,
    onDone: () -> Unit
) {
    scope.launch {
        try {
            val file = java.io.File(context.cacheDir, "inv_ocr_${System.currentTimeMillis()}.jpg")
            val ok = imageCapture.takePictureToFile(file, executor)
            val ocr = if (ok) {
                withContext(Dispatchers.Default) {
                    val bitmap = decodeSampled(file, 1600)
                    bitmap?.let { OcrReader.read(it) }
                }
            } else {
                null
            }
            file.delete()
            if (ocr != null && ocr.text.isNotBlank()) {
                viewModel.onOcrCapturado(ocr.text, ocr.lines)
            } else {
                viewModel.onOcrCapturado("", emptyList())
            }
        } catch (_: Exception) {
            viewModel.onOcrCapturado("", emptyList())
        }
        onDone()
    }
}

private suspend fun ImageCapture.takePictureToFile(file: java.io.File, executor: Executor): Boolean =
    suspendCoroutine { cont ->
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        takePicture(
            options,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    cont.resume(true)
                }

                override fun onError(exception: ImageCaptureException) {
                    cont.resume(false)
                }
            }
        )
    }

private fun decodeSampled(file: java.io.File, maxDim: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(file.absolutePath, options)
}
