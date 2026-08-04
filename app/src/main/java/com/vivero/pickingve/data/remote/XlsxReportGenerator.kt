package com.vivero.pickingve.data.remote

import java.io.File
import java.io.FileOutputStream
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Generates the .xlsx report with EXACTLY the same layout as the client's
 * reference file (see Documentacion/picking_260833_I.xlsx):
 *
 * Row 1 (metadata): ID punteo | Matrícula de camion | Matrícula de remolque | Finca | Zona | Peso de la carga
 * Row 2 (headers):  Correo empleado | Número de pedido | EAN Variante | Cantidad | Hora y fecha | Lote | Variedad
 * Rows 3+: data rows (Hora y fecha written as Excel serial number, numFmt 22).
 */
object XlsxReportGenerator {

    data class ReportRow(
        val eanVariante: String,
        val cantidad: Int,
        val timestamp: Long,
        val lote: String = "",
        val variedad: String = ""
    )

    fun generate(
        file: File,
        idPunteo: String,
        matriculaCamion: String = "",
        matriculaRemolque: String = "",
        finca: String = "",
        zona: String = "",
        pesoCarga: String = "",
        employeeEmail: String,
        orderNumber: String,
        rows: List<ReportRow>
    ) {
        val sheetXml = buildSheetXml(
            rows = rows,
            email = employeeEmail,
            orderNumber = orderNumber,
            idPunteo = idPunteo,
            matriculaCamion = matriculaCamion,
            matriculaRemolque = matriculaRemolque,
            finca = finca,
            zona = zona,
            pesoCarga = pesoCarga
        )
        writeZip(file, sheetXml)
    }

    private fun buildSheetXml(
        rows: List<ReportRow>,
        email: String,
        orderNumber: String,
        idPunteo: String,
        matriculaCamion: String,
        matriculaRemolque: String,
        finca: String,
        zona: String,
        pesoCarga: String
    ): String {
        val sb = StringBuilder()
        sb.append(
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><dimension ref=\"A1:G${rows.size + 2}\"/><sheetViews><sheetView workbookViewId=\"0\"/></sheetViews><sheetFormatPr defaultRowHeight=\"15\"/><sheetData>"
        )

        // Row 1: metadata
        sb.append(
            "<row r=\"1\">" +
                inlineStrCell("A1", "ID punteo: " + idPunteo) +
                inlineStrCell("B1", "Matrícula de camion: " + matriculaCamion) +
                inlineStrCell("C1", "Matrícula de remolque: " + matriculaRemolque) +
                inlineStrCell("D1", "Finca: " + finca) +
                inlineStrCell("E1", "Zona: " + zona) +
                inlineStrCell("F1", "Peso de la carga: " + pesoCarga) +
                "</row>"
        )

        // Row 2: headers
        sb.append(
            "<row r=\"2\">" +
                inlineStrCell("A2", "Correo empleado") +
                inlineStrCell("B2", "Número de pedido") +
                inlineStrCell("C2", "EAN Variante") +
                inlineStrCell("D2", "Cantidad") +
                inlineStrCell("E2", "Hora y fecha") +
                inlineStrCell("F2", "Lote") +
                inlineStrCell("G2", "Variedad") +
                "</row>"
        )

        // Data rows
        rows.forEachIndexed { index, row ->
            val r = index + 3
            sb.append("<row r=\"$r\">")
            sb.append(inlineStrCell("A$r", email))
            sb.append(numberCell("B$r", orderNumber))
            sb.append(inlineStrCell("C$r", row.eanVariante))
            sb.append(numberCell("D$r", row.cantidad))
            sb.append(dateCell("E$r", row.timestamp))
            sb.append(inlineStrCell("F$r", row.lote))
            sb.append(inlineStrCell("G$r", row.variedad))
            sb.append("</row>")
        }

        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun inlineStrCell(ref: String, value: String): String {
        val escaped = escapeXml(value)
        return "<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">$escaped</t></is></c>"
    }

    private fun numberCell(ref: String, value: Number): String =
        "<c r=\"$ref\"><v>$value</v></c>"

    private fun numberCell(ref: String, value: String): String =
        "<c r=\"$ref\"><v>$value</v></c>"

    /** Date as Excel serial number, style index 1 (numFmt 22 = m/d/yy h:mm). */
    private fun dateCell(ref: String, millis: Long): String {
        val serial = toExcelSerial(millis)
        return "<c r=\"$ref\" s=\"1\"><v>$serial</v></c>"
    }

    private fun toExcelSerial(millis: Long): Double {
        val offset = TimeZone.getDefault().getOffset(millis)
        val local = millis + offset
        val days = local / 86_400_000L + 25569L
        val fraction = (local % 86_400_000L).toDouble() / 86_400_000.0
        return days + fraction
    }

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private fun writeZip(file: File, sheetXml: String) {
        FileOutputStream(file).use { fos ->
            ZipOutputStream(fos).use { zip ->
                zip.putNextEntry(ZipEntry("[Content_Types].xml"))
                zip.write(CONTENT_TYPES.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("_rels/.rels"))
                zip.write(RELS.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("xl/workbook.xml"))
                zip.write(WORKBOOK.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
                zip.write(WORKBOOK_RELS.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("xl/styles.xml"))
                zip.write(STYLES.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
                zip.write(sheetXml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }

    private val CONTENT_TYPES =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
            "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
            "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
            "</Types>\n"

    private val RELS =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
            "</Relationships>\n"

    private val WORKBOOK =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
            "<sheets><sheet name=\"Picking\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>\n"

    private val WORKBOOK_RELS =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
            "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
            "</Relationships>\n"

    private val STYLES =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
            "<fonts count=\"1\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>" +
            "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>" +
            "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
            "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
            "<cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
            "<xf numFmtId=\"22\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/></cellXfs>" +
            "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
            "</styleSheet>\n"
}