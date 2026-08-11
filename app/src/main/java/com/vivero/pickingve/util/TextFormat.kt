package com.vivero.pickingve.util

fun formatInstrucciones(raw: String): String {
    if (raw.isBlank()) return raw
    val normalized = raw.replace("\r\n", "\n").replace('\r', '\n')
    return normalized
        .replace(Regex("\\s+-(?=\\s*\\S)"), "\n- ")
        .trim()
        .replace(Regex("\\n+"), "\n")
}
