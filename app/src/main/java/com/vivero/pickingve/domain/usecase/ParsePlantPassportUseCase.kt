package com.vivero.pickingve.domain.usecase

import com.vivero.pickingve.data.local.entities.ProductEntity

data class PassportData(
    val referencia: String,
    val litraje: String? = null,
    val sector: String? = null
)

/**
 * OCR post-processing for the plant passport label (recuadro negro "C: ...",
 * litraje debajo del recuadro, descripción y sector debajo).
 * Busca en el catálogo por referencia C: y desambigua con litraje y sector.
 */
class ParsePlantPassportUseCase {

    /** Extrae referencia (C:), litraje y sector del texto OCR. */
    fun parse(rawText: String): PassportData? {
        val normalized = rawText.lowercase()
            .replace("\u0000", "")
            .replace("\\s+".toRegex(), " ")
            .trim()
        if (normalized.isBlank()) return null

        val refMatch = Regex("\\bc\\s*[:]\\s*([a-z0-9][a-z0-9\\-./ ]{0,30}?)(?:\\s+[a-z]|$)")
            .find(normalized)
        val referencia = refMatch?.groupValues?.get(1)?.trim()?.uppercase() ?: return null
        if (referencia.length < 2) return null

        val litraje = detectarLitraje(normalized, referencia)
        val sector = detectarSector(normalized)

        return PassportData(referencia, litraje, sector)
    }

    /** Devuelve el producto del catálogo que encaja, o null si no hay match claro. */
    fun bestMatch(passport: PassportData, catalog: List<ProductEntity>): ProductEntity? {
        val candidatos = catalog.filter {
            it.reference.equals(passport.referencia, ignoreCase = true) ||
                it.id.equals(passport.referencia, ignoreCase = true)
        }
        if (candidatos.isEmpty()) return null
        if (candidatos.size == 1) return candidatos.first()

        return desambiguar(candidatos, passport)
    }

    fun bestMatch(rawText: String, catalog: List<ProductEntity>): ProductEntity? {
        val passport = parse(rawText) ?: return null
        return bestMatch(passport, catalog)
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

    private fun detectarLitraje(texto: String, referencia: String): String? {
        val refNorm = referencia.lowercase()
        return Regex("\\b(\\d{1,3}(?:[.,]\\d+)?)\\s*l(?:itros|itro|t)?\\b")
            .findAll(texto)
            .mapNotNull { m ->
                val match = m.value.lowercase()
                if (refNorm.contains(match.replace(" ", ""))) null else m.groupValues[1]
            }
            .firstOrNull()
    }

    private fun detectarSector(texto: String): String? {
        Regex("sector\\s*[:]?\\s*([a-z0-9]{1,4})").find(texto)?.let { return it.groupValues[1] }
        val lineas = texto.split(" ").filter { it.isNotBlank() }
        val ultima = lineas.lastOrNull()?.trim() ?: return null
        if (Regex("^[a-z0-9]{1,4}$").matches(ultima)) return ultima
        return null
    }
}