package com.vivero.pickingve.domain.usecase

import com.vivero.pickingve.domain.model.ScanResult
import com.vivero.pickingve.util.Constants

/**
 * Anti-duplicate scan guard.
 *
 * Realizes the requirement: "no permitir leer una misma etiqueta varias veces".
 * If the same barcode/OCR key is scanned again within [debounceMs] milliseconds,
 * the scan is discarded (returns null) to avoid repeated captures when the
 * camera is not moved.
 */
class ScanDebouncer(
    private val debounceMs: Long = Constants.DEFAULT_DEBOUNCE_MS
) {

    private var lastKey: String? = null
    private var lastTimestamp: Long = 0L

    /**
     * @return the same [ScanResult] if accepted, or null if it's a duplicate scan.
     */
    fun tryAccept(scan: ScanResult): ScanResult? {
        val now = System.currentTimeMillis()
        val key = scan.ean ?: scan.ocrText ?: return null

        return if (key == lastKey && now - lastTimestamp < debounceMs) {
            null // Duplicate, camera not moved
        } else {
            lastKey = key
            lastTimestamp = now
            scan
        }
    }

    fun reset() {
        lastKey = null
        lastTimestamp = 0L
    }
}