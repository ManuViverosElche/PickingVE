package com.vivero.pickingve.domain.usecase

import com.vivero.pickingve.data.local.entities.ProductEntity
import org.junit.Assert.*
import org.junit.Test

class MatchOcrUseCaseTest {

    private val useCase = MatchOcrUseCase()

    private val catalog = listOf(
        ProductEntity(
            id = "MAC-25L-A",
            reference = "MAC-25L-A",
            ean = "8412345678901",
            name = "Maceta Olearia 25L",
            defaultLiters = 25f,
            defaultMeasure = "80-100cm",
            defaultCaliber = "C25"
        ),
        ProductEntity(
            id = "MAC-3L-RO",
            reference = "MAC-3L-RO",
            ean = "8412345678902",
            name = "Maceta Rosal 3L",
            defaultLiters = 3f,
            defaultMeasure = "20-30cm",
            defaultCaliber = "C14"
        )
    )

    @Test
    fun `matches product by exact name or reference`() {
        val (match, score) = useCase.bestMatch("Maceta Olearia 25L", catalog)
        assertNotNull(match)
        assertEquals("MAC-25L-A", match?.reference)
        assertTrue(score >= 0.6f)
    }

    @Test
    fun `matches product by reference code`() {
        val (match, score) = useCase.bestMatch("MAC-3L-RO", catalog)
        assertNotNull(match)
        assertEquals("MAC-3L-RO", match?.reference)
        assertTrue(score >= 0.6f)
    }

    @Test
    fun `returns null when no match found`() {
        val (match, score) = useCase.bestMatch("Unknown Plant 99L", catalog)
        assertNull(match)
        assertTrue(score < 0.6f)
    }
}
