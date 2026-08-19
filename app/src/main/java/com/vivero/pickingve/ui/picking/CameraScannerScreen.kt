package com.vivero.pickingve.ui.picking

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
import androidx.camera.core.CameraUnavailableException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import java.util.concurrent.Executor
import java.util.concurrent.Executors

enum class CameraModo { SCAN, PASAPORTE, AMBOS }

@Composable
fun CameraScannerScreen(
    viewModel: PickingViewModel,
    onClose: () -> Unit,
    modoInicial: CameraModo = CameraModo.AMBOS
) {
    val context = LocalContext.current
    val lifecycleOwner = context as? LifecycleOwner
    val debouncer = remember { ScanDebouncer() }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(executor) {
        onDispose {
            executor.shutdown()
        }
    }

    var modo by remember { mutableStateOf(modoInicial) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var ocrLoading by remember { mutableStateOf(false) }
    var avisoPasaporte by remember { mutableStateOf(false) }
    var codigoVisto by remember { mutableStateOf(false) }
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

    LaunchedEffect(avisoPasaporte) {
        if (avisoPasaporte) {
            delay(3000)
            avisoPasaporte = false
        }
    }

    if (permissionDenied) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
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
            verticalArrangement = Arrangement.Center
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

    fun lanzarOcr() {
        val capture = imageCapture
        if (capture == null) {
            avisoPasaporte = true
            return
        }
        ocrLoading = true
        runOcr(context, capture, executor, viewModel) { ocrLoading = false }
    }

    fun onCodigoLeido(codigo: String) {
        codigoVisto = true
        if (modo == CameraModo.PASAPORTE) return
        val esEan = Regex("^\\d{8}$|^\\d{13}$").matches(codigo)
        if (esEan) viewModel.onBarcodeScanned(codigo) else avisoPasaporte = true
    }

    LaunchedEffect(modo, codigoVisto) {
        if (!codigoVisto) {
            delay(4000)
            if (!codigoVisto) avisoPasaporte = true
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
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
                            .also { it.setAnalyzer(executor, BarcodeAnalyzer(debouncer, ::onCodigoLeido)) }

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
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (avisoPasaporte) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "¿Etiqueta sin EAN? Pulsa CAPTURAR ETIQUETA SIN EAN",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                Text(
                    text = when (modo) {
                        CameraModo.SCAN -> "Enfoca el código EAN de la etiqueta"
                        CameraModo.PASAPORTE -> "Captura la etiqueta sin EAN (pasaporte)"
                        CameraModo.AMBOS -> "Enfoca el código EAN o captura la etiqueta sin EAN"
                    },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 8.dp)
                ) {
                    CameraModo.entries.forEach { m ->
                        val activo = modo == m
                        Surface(
                            onClick = { modo = m; codigoVisto = false },
                            shape = MaterialTheme.shapes.small,
                            color = if (activo) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = when (m) {
                                    CameraModo.SCAN -> "EAN"
                                    CameraModo.PASAPORTE -> "Pasaporte"
                                    CameraModo.AMBOS -> "Ambos"
                                },
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (activo) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Button(
                    onClick = { lanzarOcr() },
                    enabled = !ocrLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    if (ocrLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Filled.DocumentScanner, contentDescription = null)
                    }
                    Text(" Capturar etiqueta sin EAN")
                }
            }
        }
    }
}

private fun runOcr(
    context: android.content.Context,
    imageCapture: ImageCapture,
    executor: Executor,
    viewModel: PickingViewModel,
    onDone: () -> Unit
) {
    CoroutineScope(Dispatchers.Main).launch {
        try {
            val file = java.io.File(context.cacheDir, "ocr_${System.currentTimeMillis()}.jpg")
            val ok = imageCapture.takePictureToFile(file, executor)
            val text = if (ok) {
                withContext(Dispatchers.Default) {
                    val bitmap = decodeSampled(file, 1600)
                    bitmap?.let { OcrReader.readText(it) }
                }
            } else {
                null
            }
            file.delete()
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

/** Decodes a JPEG file capping the longest side to maxDim (avoids OOM on 12MP captures). */
private fun decodeSampled(file: java.io.File, maxDim: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(file.absolutePath, options)
}