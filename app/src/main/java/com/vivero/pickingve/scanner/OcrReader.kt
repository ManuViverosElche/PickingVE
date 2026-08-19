package com.vivero.pickingve.scanner

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/** Línea de texto reconocida por el OCR con su posición en la imagen (px). */
data class OcrLine(
    val text: String,
    val top: Int,
    val bottom: Int,
    val height: Int
)

/** Resultado OCR: texto plano + líneas ordenadas de arriba a abajo. */
data class OcrResult(
    val text: String,
    val lines: List<OcrLine>
)

/**
 * OCR fallback: captures label text from a bitmap frame when no barcode is
 * present or when the operator presses the OCR button manually.
 */
object OcrReader {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * @return the recognized text with per-line geometry, or null.
     */
    suspend fun read(bitmap: Bitmap): OcrResult? {
        val scaled = upscale(bitmap)
        val image = InputImage.fromBitmap(scaled, 0)
        return try {
            val result = recognizer.process(image).await()
            val lines = result.textBlocks
                .flatMap { block -> block.lines }
                .mapNotNull { line ->
                    val box = line.boundingBox ?: return@mapNotNull null
                    val text = line.text.trim()
                    if (text.isEmpty()) null
                    else OcrLine(text, box.top, box.bottom, box.height())
                }
                .sortedWith(compareBy({ it.top }, { it.bottom }))
            val text = result.text.trim()
            if (text.isEmpty()) null else OcrResult(text, lines)
        } catch (e: Exception) {
            Log.e(TAG, "OCR failed", e)
            null
        }
    }

    /** @return the raw concatenated text recognized on the label, or null. */
    suspend fun readText(bitmap: Bitmap): String? = read(bitmap)?.text

    /** MLKit latin recognizer reads small text better when upscaled. */
    private fun upscale(bitmap: Bitmap): Bitmap {
        val maxDim = maxOf(bitmap.width, bitmap.height)
        if (maxDim >= 1600) return bitmap
        val scale = (1600f / maxDim).coerceAtLeast(2f)
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt(),
            (bitmap.height * scale).toInt(),
            true
        )
    }

    private fun Rect.height(): Int = bottom - top

    private val TAG = "OcrReader"
}

private suspend fun <T> Task<T>.await(): T = suspendCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}