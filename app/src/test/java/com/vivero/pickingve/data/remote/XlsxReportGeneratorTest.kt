package com.vivero.pickingve.data.remote

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class XlsxReportGeneratorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `generates valid zip xlsx file`() {
        val file = tempFolder.newFile("test_report.xlsx")
        val rows = listOf(
            XlsxReportGenerator.ReportRow(
                eanVariante = "8412345678901",
                cantidad = 5,
                timestamp = System.currentTimeMillis(),
                medida = "C25",
                variedad = "Maceta Olearia 25L",
                refPedida = "MAC-25L-A"
            )
        )

        XlsxReportGenerator.generate(
            file = file,
            idPunteo = "test-uuid-123",
            matriculaCamion = "1234ABC",
            matriculaRemolque = "5678DEF",
            finca = "BORISA",
            zona = "Zona 1",
            pesoCarga = "1500",
            employeeEmail = "operario@vivero.com",
            orderNumber = "10045",
            rows = rows
        )

        assertTrue(file.exists())
        assertTrue(file.length() > 0)

        // Verify it is a valid ZIP archive containing sheet1.xml and workbook.xml
        java.util.zip.ZipFile(file).use { zip ->
            assertNotNull(zip.getEntry("xl/worksheets/sheet1.xml"))
            assertNotNull(zip.getEntry("xl/workbook.xml"))
            assertNotNull(zip.getEntry("[Content_Types].xml"))
        }
    }

    @Test
    fun `sheet includes ref pedida column and delta only pending rows`() {
        val file = tempFolder.newFile("test_report_ref.xlsx")
        val rows = listOf(
            XlsxReportGenerator.ReportRow(
                eanVariante = "8412345678901",
                cantidad = 3,
                timestamp = System.currentTimeMillis(),
                variedad = "",
                refPedida = "MAC-25L-A"
            )
        )

        XlsxReportGenerator.generate(
            file = file,
            idPunteo = "test-uuid-2",
            employeeEmail = "operario@vivero.com",
            orderNumber = "10045",
            rows = rows
        )

        java.util.zip.ZipFile(file).use { zip ->
            val sheet = zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml"))
                .readBytes().toString(Charsets.UTF_8)
            assertTrue(sheet.contains("H2"))
            assertTrue(sheet.contains("Ref. pedida"))
            assertTrue(sheet.contains("H3"))
            assertTrue(sheet.contains("MAC-25L-A"))
            assertTrue(sheet.contains("dimension ref=\"A1:J3\""))
        }
    }
}
