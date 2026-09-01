package com.vivero.pickingve.scanner

import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.vivero.pickingve.domain.usecase.ScanDebouncer

/**
 * Analyzes camera frames in real time, detects EAN barcodes and routes
 * accepted (non-debounced) scans to the callback.
 */
@ExperimentalGetImage
class BarcodeAnalyzer(
    private val debouncer: ScanDebouncer,
    private val onBarcode: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner: BarcodeScanner = BarcodeScanning.getClient()

    override fun analyze(imageProxy: ImageProxy) {
        var handedToScanner = false
        try {
            val mediaImage = imageProxy.image
            if (mediaImage == null) return

            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            val processing = scanner.process(image)
            handedToScanner = true
            processing
                .addOnSuccessListener { barcodes ->
                    barcodes.forEach { barcode ->
                        val format = barcode.format
                        if (format == Barcode.FORMAT_EAN_13 ||
                            format == Barcode.FORMAT_EAN_8 ||
                            format == Barcode.FORMAT_CODE_128 ||
                            format == Barcode.FORMAT_DATA_MATRIX
                        ) {
                            barcode.rawValue?.let { raw ->
                                val accepted = debouncer.tryAccept(
                                    com.vivero.pickingve.domain.model.ScanResult(
                                        ean = raw.trim(),
                                        ocrText = null
                                    )
                                )
                                if (accepted != null) {
                                    onBarcode(raw.trim())
                                }
                            }
                        }
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Barcode processing failed", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Could not prepare barcode frame", e)
        } finally {
            // No completion callback exists when frame preparation fails.
            if (!handedToScanner) imageProxy.close()
        }
    }

    private companion object {
        const val TAG = "BarcodeAnalyzer"
    }
}
