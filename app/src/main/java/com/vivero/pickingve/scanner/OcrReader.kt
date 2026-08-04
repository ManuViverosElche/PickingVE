package com.vivero.pickingve.scanner

import android.graphics.Bitmap
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * OCR fallback: captures label text from a bitmap frame when no barcode is
 * present or when the operator presses the OCR button manually.
 */
object OcrReader {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * @return the raw concatenated text recognized on the label, or null.
     */
    suspend fun readText(bitmap: Bitmap): String? {
        val image = InputImage.fromBitmap(bitmap, 0)
        return try {
            val result = recognizer.process(image).await()
            val text = result.text.trim()
            if (text.isEmpty()) null else text
        } catch (e: Exception) {
            Log.e(TAG, "OCR failed", e)
            null
        }
    }

    private val TAG = "OcrReader"
}

private suspend fun <T> Task<T>.await(): T = suspendCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}
