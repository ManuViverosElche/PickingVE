package com.vivero.pickingve.domain.usecase

import com.vivero.pickingve.data.local.entities.ProductEntity

/**
 * OCR post-processing: takes the raw text captured from a label and tries to
 * match it against the local product catalog (fuzzy matching by name/reference).
 */
class MatchOcrUseCase {

    /**
     * Returns the best product match for a raw OCR text block, or null (with
     * its score) if the best match is below [minScore].
     */
    fun bestMatch(
        rawText: String,
        catalog: List<ProductEntity>,
        minScore: Float = MIN_SCORE
    ): Pair<ProductEntity?, Float> {
        val normalized = rawText.lowercase()
            .replace("\\s+".toRegex(), " ")
            .trim()

        var best: ProductEntity? = null
        var bestScore = 0f

        for (product in catalog) {
            val name = product.name.lowercase()
            val ref = product.reference.lowercase()
            val score = maxOf(
                fuzzyContainment(name, normalized),
                fuzzyContainment(ref, normalized)
            )
            if (score > bestScore) {
                bestScore = score
                best = product
            }
        }

        return if (bestScore >= minScore) best to bestScore else null to bestScore
    }

    /** Word-by-word containment: fraction of query tokens present in candidate. */
    private fun fuzzyContainment(candidate: String, query: String): Float {
        if (candidate.isBlank() || query.isBlank()) return 0f
        val queryTokens = query.split(" ").filter { it.length >= 3 }
        if (queryTokens.isEmpty()) return 0f
        val matches = queryTokens.count { token -> candidate.contains(token) }
        return matches.toFloat() / queryTokens.size
    }

    private companion object {
        const val MIN_SCORE = 0.6f
    }
}