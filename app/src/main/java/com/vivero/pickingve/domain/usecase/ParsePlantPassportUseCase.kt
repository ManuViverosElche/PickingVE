package com.vivero.pickingve.domain.usecase

import com.vivero.pickingve.data.local.entities.LitrajeEntity
import com.vivero.pickingve.data.local.entities.ProductEntity
import com.vivero.pickingve.data.local.entities.SectorEntity
import com.vivero.pickingve.scanner.OcrLine

data class PassportData(
    val referencia: String,
    val litraje: String? = null,
    val litrajeDesc: String? = null,
    val sector: String? = null,
    val sectorDesc: String? = null
)

/**
 * OCR post-processing for the plant passport label.
 *
 * Estructura física de la etiqueta (de arriba a abajo):
 *   1. Recuadro negro con A/B/C/D (C: <referencia>; D: ES = código de país, NUNCA sector).
 *   2. Litraje (justo debajo del recuadro, FUENTE MÁS GRANDE de la etiqueta; puede no
 *      aparecer y puede ser un texto: "MALLA", "AZUL", "ROSA CLARO"...).
 *   3. Descripción auxiliar (SIEMPRE en cursiva y con fuente menor que el litraje; se OBVIA).
 *   4. Sector (debajo de la descripción; puede no aparecer).
 *
 * Con las líneas del OCR (posición + altura = tamaño de fuente aproximado) se identifica
 * el litraje como la línea de mayor altura debajo del recuadro C:, y la descripción
 * auxiliar queda descartada porque tiene fuente menor y no matchea la tabla LITRAJES.
 * Tanto el litraje como el sector se validan contra las tablas LITRAJES/SECTORES
 * (por id o descripción normalizada), lo que permite litrajes en palabra.
 */
class ParsePlantPassportUseCase {

    /** Extrae referencia (C:), litraje y sector del texto OCR respetando el orden de la etiqueta. */
    fun parse(rawText: String): PassportData? = parse(rawText, emptyList(), emptyList(), emptyList())

    /** Extrae referencia, litraje y sector usando la geometría de las líneas cuando está disponible. */
    fun parse(
        rawText: String,
        lines: List<OcrLine>,
        litrajes: List<LitrajeEntity>,
        sectores: List<SectorEntity>
    ): PassportData? {
        val text = rawText.replace("\u0000", "")
            .replace("\\r".toRegex(), "")
            .trim()
        if (text.isBlank()) return null

        val lineas = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val referencia = detectarReferencia(text, lineas) ?: return null

        val litrajeDesc = if (lines.isNotEmpty()) {
            detectarLitrajeGeometria(lines, litrajes, referencia) ?: detectarLitraje(text, lineas, referencia)
        } else {
            detectarLitraje(text, lineas, referencia)
        }
        val sectorDesc = if (lines.isNotEmpty()) {
            detectarSectorGeometria(lines, sectores, referencia) ?: detectarSector(text, lineas, referencia)
        } else {
            detectarSector(text, lineas, referencia)
        }

        return PassportData(
            referencia,
            litraje = resolveLitraje(litrajeDesc, litrajes),
            litrajeDesc = litrajeDesc,
            sector = resolveSector(sectorDesc, sectores),
            sectorDesc = sectorDesc
        )
    }

    private fun detectarReferencia(texto: String, lineas: List<String>): String? {
        for (linea in lineas) {
            val m = Regex("\\bc[\\s.:]*\\s*([a-z0-9][a-z0-9./\\-]{1,30})", RegexOption.IGNORE_CASE)
                .find(linea)
            if (m != null) {
                val ref = m.groupValues[1].trimEnd('.', ',', '/', '-').uppercase()
                if (ref.length >= 2) return ref
            }
        }
        // Sin "C:": preferir un token con letras+guiones (formato REF-NNN) antes que un número suelto.
        Regex("\\b([0-9]{2,}[a-z0-9./\\-]*[a-z][a-z0-9./\\-]*)\\b", RegexOption.IGNORE_CASE)
            .find(texto)?.let {
                val ref = it.groupValues[1].trimEnd('.', ',', '/', '-').uppercase()
                if (ref.length >= 3) return ref
            }
        return Regex("\\b([0-9][a-z0-9./\\-]{2,30})\\b")
            .find(texto)
            ?.groupValues?.get(1)
            ?.trimEnd('.', ',', '/', '-')
            ?.uppercase()
            ?.takeIf { it.length >= 3 }
    }

    /**
     * El litraje es la línea de MAYOR ALTURA (fuente más grande) entre las que están
     * debajo del recuadro C:. Se valida contra la tabla LITRAJES (id/descripción
     * normalizada, incluye palabras como "MALLA") o contra el patrón numérico.
     * Si la línea más alta no es un litraje, no hay litraje legible.
     */
    private fun detectarLitrajeGeometria(
        lines: List<OcrLine>,
        litrajes: List<LitrajeEntity>,
        referencia: String
    ): String? {
        val idxC = lines.indexOfFirst { contieneReferenciaC(it.text) }
        val debajo = if (idxC >= 0) lines.drop(idxC + 1) else lines
        if (debajo.isEmpty()) return null

        val porTamano = debajo.sortedByDescending { it.height }
        val masAlta = porTamano.firstOrNull() ?: return null
        val texto = limpiarToken(masAlta.text)
        if (texto.isBlank()) return null
        if (referencia.lowercase().contains(texto.lowercase().replace(" ", ""))) return null
        if (esLitrajeConocido(texto, litrajes)) return texto.uppercase()
        if (patronLitraje.matches(masAlta.text.trim())) return texto.uppercase()
        return null
    }

    /**
     * El sector es una línea debajo del recuadro C: (sin contar la del litraje) cuyo
     * texto matchea la tabla SECTORES por id o descripción normalizada. Se excluyen
     * "ES" (código de país D:), tokens con GGN o asteriscos y años sueltos.
     */
    private fun detectarSectorGeometria(
        lines: List<OcrLine>,
        sectores: List<SectorEntity>,
        referencia: String
    ): String? {
        if (sectores.isEmpty()) return null
        val idxC = lines.indexOfFirst { contieneReferenciaC(it.text) }
        val debajo = if (idxC >= 0) lines.drop(idxC + 1) else lines
        val litrajeIdx = debajo.withIndex().maxByOrNull { it.value.height }?.index

        for ((i, linea) in debajo.withIndex()) {
            if (i == litrajeIdx) continue
            val texto = limpiarToken(linea.text)
            if (texto.isBlank()) continue
            if (esSectorInvalido(texto, referencia)) continue
            val match = sectores.firstOrNull {
                it.id.equals(texto, ignoreCase = true) ||
                    normalizarDesc(it.descripcion) == normalizarDesc(texto)
            } ?: continue
            return match.descripcion.ifBlank { match.id }
        }
        return null
    }

    private fun contieneReferenciaC(linea: String): Boolean =
        Regex("\\bc\\s*[:.]", RegexOption.IGNORE_CASE).containsMatchIn(linea)

    private fun esLitrajeConocido(texto: String, litrajes: List<LitrajeEntity>): Boolean {
        if (litrajes.isEmpty()) return false
        val norm = normalizarDesc(texto)
        if (norm.length < 2) return false
        return litrajes.any { normalizarDesc(it.id) == norm || normalizarDesc(it.descripcion) == norm }
    }

    private fun normalizarDesc(texto: String): String =
        texto.lowercase().replace(" ", "")

    private val patronLitraje = Regex(
        "^\\s*(\\d{1,4}(?:[.,]\\d+)?\\s*\\+?\\s*L?|T\\d+|\\d{1,4}/\\d{1,2})\\s*$",
        RegexOption.IGNORE_CASE
    )

    /** Devuelve los productos del catálogo cuya referencia coincide (normalizando guiones/espacios). */
    fun buscarPorReferencia(referencia: String, catalog: List<ProductEntity>): List<ProductEntity> {
        val norm = normalizarRef(referencia)
        if (norm.isEmpty()) return emptyList()
        return catalog.filter {
            normalizarRef(it.reference) == norm || normalizarRef(it.id) == norm
        }
    }

    /** Quita guiones, espacios y signos para comparar referencias leídas por OCR. */
    fun normalizarRef(ref: String): String =
        ref.uppercase().filter { it.isLetterOrDigit() }

    /** Devuelve el producto del catálogo que encaja, o null si no hay match claro. */
    fun bestMatch(
        passport: PassportData,
        catalog: List<ProductEntity>,
        litrajes: List<LitrajeEntity> = emptyList(),
        sectores: List<SectorEntity> = emptyList()
    ): ProductEntity? {
        val candidatos = buscarPorReferencia(passport.referencia, catalog)
        if (candidatos.isEmpty()) return null
        if (candidatos.size == 1) return candidatos.first()

        val litrajeCodigo = passport.litraje ?: resolveLitraje(passport.litrajeDesc, litrajes)
        val sectorCodigo = passport.sector ?: resolveSector(passport.sectorDesc, sectores)

        return desambiguar(
            candidatos,
            passport.copy(litraje = litrajeCodigo, sector = sectorCodigo)
        )
    }

    fun bestMatch(
        rawText: String,
        catalog: List<ProductEntity>,
        litrajes: List<LitrajeEntity> = emptyList(),
        sectores: List<SectorEntity> = emptyList()
    ): ProductEntity? {
        val passport = parse(rawText) ?: return null
        return bestMatch(passport, catalog, litrajes, sectores)
    }

    /** Convierte la descripción OCR del litraje en su código (ID_LITRAJE). */
    fun resolveLitraje(desc: String?, litrajes: List<LitrajeEntity>): String? {
        if (desc.isNullOrBlank() || litrajes.isEmpty()) return null
        val norm = desc.lowercase().replace(" ", "")
        return litrajes.firstOrNull {
            it.id.lowercase().replace(" ", "") == norm ||
                it.descripcion.lowercase().replace(" ", "") == norm
        }?.id
    }

    /** Convierte la descripción OCR del sector en su código (ID_SECTOR). */
    fun resolveSector(desc: String?, sectores: List<SectorEntity>): String? {
        if (desc.isNullOrBlank() || sectores.isEmpty()) return null
        val norm = desc.lowercase().trim()
        if (norm.length < 2) return null
        return sectores.firstOrNull {
            it.id.equals(norm, ignoreCase = true) ||
                it.descripcion.lowercase() == norm ||
                (norm.length >= 3 &&
                    (it.descripcion.lowercase().contains(norm) || norm.contains(it.descripcion.lowercase())))
        }?.id
    }

    private fun desambiguar(candidatos: List<ProductEntity>, passport: PassportData): ProductEntity? {
        var restantes = candidatos
        passport.litraje?.let { litraje ->
            val litrajeNorm = litraje.lowercase().replace(" ", "")
            val litrajeFloat = litraje.replace(",", ".").toFloatOrNull()
            val filtrados = restantes.filter {
                it.litraje.equals(litrajeNorm, ignoreCase = true) ||
                    (it.defaultLiters != null && litrajeFloat != null &&
                        Math.abs(it.defaultLiters - litrajeFloat) < 0.01f)
            }
            if (filtrados.isNotEmpty()) restantes = filtrados
        }
        passport.sector?.let { sector ->
            val filtrados = restantes.filter { it.sector.equals(sector, ignoreCase = true) }
            if (filtrados.isNotEmpty()) restantes = filtrados
        }
        return if (restantes.size == 1) restantes.first() else null
    }

    /**
     * El litraje es el dato que va justo debajo del recuadro C:. Si el OCR
     * devuelve varias líneas, se miran las 3 primeras después de la línea C:.
     */
    private fun detectarLitraje(texto: String, lineas: List<String>, referencia: String): String? {
        val idxC = lineas.indexOfFirst {
            Regex("\\bc\\s*[:.]", RegexOption.IGNORE_CASE).containsMatchIn(it)
        }
        val inicio = if (idxC >= 0) idxC + 1 else 0
        val candidatas = lineas.subList(inicio, lineas.size).take(3)
        for (linea in candidatas) {
            if (Regex("\\bc\\s*[:.]", RegexOption.IGNORE_CASE).containsMatchIn(linea)) continue
            val m = patronLitraje.find(linea)
            if (m != null) {
                val match = m.value.trim()
                if (referencia.lowercase().contains(match.lowercase().replace(" ", ""))) continue
                return match.uppercase()
            }
        }
        // El OCR devolvió todo en una sola línea: buscar el patrón en todo el texto.
        return Regex("\\b(\\d{1,4}(?:[.,]\\d+)?\\s*\\+?\\s*L)\\b", RegexOption.IGNORE_CASE)
            .find(texto)?.groupValues?.get(1)?.trim()?.uppercase()
            ?.takeIf { !referencia.lowercase().contains(it.lowercase().replace(" ", "")) }
            ?: Regex("\\b(T\\d+|\\d{1,4}/\\d{1,2}|\\d{1,4}\\s*\\+)\\b", RegexOption.IGNORE_CASE)
                .find(texto)?.groupValues?.get(1)?.trim()?.uppercase()
                ?.takeIf { !referencia.lowercase().contains(it.lowercase().replace(" ", "")) }
    }

    /**
     * El sector es la última línea de la etiqueta (debajo de la descripción
     * auxiliar). Solo se aceptan tokens cortos (códigos de sector).
     */
    private fun detectarSector(texto: String, lineas: List<String>, referencia: String): String? {
        Regex("sector\\s*[:]?\\s*([a-z0-9\\-]{1,12})", RegexOption.IGNORE_CASE)
            .find(texto)?.let { return it.groupValues[1].uppercase() }

        val ultima = lineas.lastOrNull()?.trim() ?: return null
        if (Regex("^[a-z0-9\\-]{1,12}$", RegexOption.IGNORE_CASE).matches(ultima)) {
            if (esSectorInvalido(ultima, referencia)) return null
            return ultima.uppercase()
        }
        val tokens = texto.split(Regex("\\s+")).filter { it.isNotBlank() }
        val ultimoToken = tokens.lastOrNull() ?: return null
        if (Regex("^[a-z0-9\\-]{1,12}$", RegexOption.IGNORE_CASE).matches(ultimoToken)) {
            if (esSectorInvalido(ultimoToken, referencia)) return null
            return ultimoToken.uppercase()
        }
        return null
    }

    /** Rechaza tokens que son la propia referencia o números sueltos (años, cantidades). */
    private fun esSectorInvalido(token: String, referencia: String): Boolean {
        val norm = token.lowercase()
        return norm.length < 2 ||
            norm == "es" ||
            norm.contains("ggn") ||
            Regex("^\\d{3,}$").matches(token) ||
            referencia.lowercase().contains(norm)
    }

    private fun limpiarToken(texto: String): String =
        texto.trim().trim('*', '.', ',', '/', '-', ' ')
}