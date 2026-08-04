package com.vivero.pickingve.domain.usecase

import com.vivero.pickingve.domain.model.ScanResult
import org.junit.Assert.*
import org.junit.Test

class ScanDebouncerTest {

    @Test
    fun `accepts first scan`() {
        val debouncer = ScanDebouncer(debounceMs = 1000L)
        val scan = ScanResult(ean = "8412345678901", ocrText = null)

        val result = debouncer.tryAccept(scan)

        assertNotNull(result)
        assertEquals("8412345678901", result?.ean)
    }

    @Test
    fun `rejects duplicate scan within debounce window`() {
        val debouncer = ScanDebouncer(debounceMs = 2000L)
        val scan = ScanResult(ean = "8412345678901", ocrText = null)

        val first = debouncer.tryAccept(scan)
        val second = debouncer.tryAccept(scan)

        assertNotNull(first)
        assertNull(second)
    }

    @Test
    fun `accepts different barcode immediately`() {
        val debouncer = ScanDebouncer(debounceMs = 2000L)
        val scan1 = ScanResult(ean = "8412345678901", ocrText = null)
        val scan2 = ScanResult(ean = "8412345678902", ocrText = null)

        val first = debouncer.tryAccept(scan1)
        val second = debouncer.tryAccept(scan2)

        assertNotNull(first)
        assertNotNull(second)
        assertEquals("8412345678902", second?.ean)
    }
}
