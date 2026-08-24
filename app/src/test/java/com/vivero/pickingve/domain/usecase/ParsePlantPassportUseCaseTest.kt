package com.vivero.pickingve.domain.usecase

import com.vivero.pickingve.data.local.entities.LitrajeEntity
import com.vivero.pickingve.data.local.entities.ProductEntity
import com.vivero.pickingve.data.local.entities.SectorEntity
import com.vivero.pickingve.scanner.OcrLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Casos extraídos de fallos reales en campo:
 * - D-138: referencias leídas con guiones/espacios variables.
 * - D-141: litraje en palabra (MALLA/AZUL) y descripción auxiliar descartada por altura.
 * - D-145: "Varios productos coinciden con C:" y tokens falsos de sector (ES/GGN/años).
 */
class ParsePlantPassportUseCaseTest {

    private val useCase = ParsePlantPassportUseCase()

    private val litrajes = listOf(
        LitrajeEntity(id = "25", descripcion = "25L"),
        LitrajeEntity(id = "16+", descripcion = "160-170L"),
        LitrajeEntity(id = "230", descripcion = "230L"),
        LitrajeEntity(id = "T90", descripcion = "T90"),
        LitrajeEntity(id = "MALLA", descripcion = "Malla")
    )

    private val sectores = listOf(
        SectorEntity(id = "BLO", descripcion = "Bloque"),
        SectorEntity(id = "SU", descripcion = "Suelo"),
        SectorEntity(id = "EXT", descripcion = "Exterior")
    )

    @Test
    fun `extrae referencia litraje y sector de etiqueta clasica sin geometria`() {
        val text = """
            A 123456
            C: 11008-BO-26
            25L
            Olea europaea
            ES
            BLO
        """.trimIndent()
        val data = useCase.parse(text, emptyList(), litrajes, sectores)

        assertNotNull(data)
        assertEquals("11008-BO-26", data?.referencia)
        assertEquals("25L", data?.litrajeDesc)
        assertEquals("25", data?.litraje)
        assertEquals("BLO", data?.sector)
    }

    @Test
    fun `D-138 referencia leida con separadores distintos casa por normalizacion`() {
        val catalog = listOf(
            product(id = "P1", reference = "11125-FA-24", liters = 90f, litraje = "T90", sector = "BLO")
        )
        val data = useCase.parse("C: 11125-FA-24\nT90\nBLO")
        assertEquals("11125-FA-24", data?.referencia)

        val encontrados = useCase.buscarPorReferencia("11125 fa 24", catalog)
        assertEquals(1, encontrados.size)
        assertEquals("P1", encontrados.first().id)
        assertTrue(useCase.normalizarRef("11008 BO-26") == useCase.normalizarRef("11008-BO-26"))
    }

    @Test
    fun `D-141 litraje en palabra se detecta por altura de fuente`() {
        val lines = listOf(
            OcrLine("C: 11125-SU-24", top = 0, bottom = 40, height = 40),
            OcrLine("MALLA", top = 50, bottom = 130, height = 80),
            OcrLine("Olearia haemasthemon", top = 140, bottom = 165, height = 25),
            OcrLine("BLO", top = 175, bottom = 195, height = 20),
            OcrLine("GGN 8438002215009", top = 205, bottom = 220, height = 15)
        )
        val data = useCase.parse(
            "C: 11125-SU-24 MALLA Olearia haemasthemon BLO GGN 8438002215009",
            lines,
            litrajes,
            sectores
        )
        assertNotNull(data)
        assertEquals("11125-SU-24", data?.referencia)
        assertEquals("MALLA", data?.litraje)
        assertEquals("MALLA", data?.litrajeDesc)
        assertEquals("BLO", data?.sector)
    }

    @Test
    fun `D-141 descripcion auxiliar con fuente menor nunca se toma como litraje`() {
        val lines = listOf(
            OcrLine("C: 11125-FA-24", top = 0, bottom = 40, height = 40),
            OcrLine("Olearia grandiflora", top = 50, bottom = 120, height = 70),
            OcrLine("SU", top = 130, bottom = 150, height = 20)
        )
        val data = useCase.parse("C: 11125-FA-24 Olearia grandiflora SU", lines, litrajes, sectores)
        assertNull(data?.litraje)
        assertNull(data?.litrajeDesc)
    }

    @Test
    fun `D-145 ES GGN y años sueltos nunca son sector`() {
        val lines = listOf(
            OcrLine("C: 11008-BO-26", top = 0, bottom = 40, height = 40),
            OcrLine("25L", top = 50, bottom = 120, height = 70),
            OcrLine("ES", top = 130, bottom = 150, height = 20),
            OcrLine("GGN 8438002215009", top = 155, bottom = 172, height = 17),
            OcrLine("2026", top = 178, bottom = 196, height = 18),
            OcrLine("BLO", top = 200, bottom = 222, height = 22)
        )
        val data = useCase.parse(
            "C: 11008-BO-26 25L ES GGN 8438002215009 2026 BLO",
            lines,
            litrajes,
            sectores
        )
        assertEquals("BLO", data?.sector)
        assertEquals("25", data?.litraje)
    }

    @Test
    fun `resolveLitraje mapea extremo de rango leido por OCR al codigo de tabla`() {
        assertEquals("16+", useCase.resolveLitraje("170L", litrajes))
        assertEquals("25", useCase.resolveLitraje("25L", litrajes))
        assertEquals("MALLA", useCase.resolveLitraje("malla", litrajes))
        assertNull(useCase.resolveLitraje("noexiste", litrajes))
        assertNull(useCase.resolveLitraje(null, litrajes))
    }

    @Test
    fun `bestMatch desambigua variantes por litraje y sector y devuelve null si sigue ambiguo`() {
        val catalog = listOf(
            product(id = "P1", reference = "11008-BO-26", liters = 13f, litraje = "13+", sector = "BLO"),
            product(id = "P2", reference = "11008-BO-26", liters = 289f, litraje = "289", sector = "SU")
        )
        val elegido = useCase.bestMatch(
            PassportData(referencia = "11008-BO-26", litraje = "13+", sector = "BLO"),
            catalog,
            litrajes,
            sectores
        )
        assertEquals("P1", elegido?.id)

        val ambiguo = useCase.bestMatch(
            PassportData(referencia = "11008-BO-26"),
            catalog,
            litrajes,
            sectores
        )
        assertNull(ambiguo)

        val unico = useCase.bestMatch(
            PassportData(referencia = "REF-UNICA"),
            listOf(product(id = "U1", reference = "REF-UNICA")),
            litrajes,
            sectores
        )
        assertEquals("U1", unico?.id)
    }

    @Test
    fun `texto vacio o sin referencia devuelve null`() {
        assertNull(useCase.parse(""))
        assertNull(useCase.parse("   \n  "))
        assertNull(useCase.parse("Olea europaea sin recuadro"))
    }

    private fun product(
        id: String,
        reference: String,
        ean: String? = null,
        liters: Float? = null,
        litraje: String = "",
        sector: String = ""
    ) = ProductEntity(
        id = id,
        reference = reference,
        ean = ean,
        name = "$reference $litraje",
        defaultLiters = liters,
        defaultMeasure = null,
        defaultCaliber = null,
        litraje = litraje,
        sector = sector
    )
}
