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
                lote = "C25",
                variedad = "Maceta Olearia 25L"
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
}
