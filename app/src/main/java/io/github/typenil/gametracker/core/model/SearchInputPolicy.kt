package io.github.typenil.gametracker.core.model

import java.text.Normalizer
import java.util.Locale

/**
 * Verdict of [SearchInputPolicy.validate]. The raw user input is never rewritten: invalid
 * characters surface as a violation and the search is not dispatched.
 */
sealed interface SearchInputValidation {
    data class Valid(val raw: String) : SearchInputValidation
    data class Invalid(val violation: SearchInputViolation) : SearchInputValidation
}

/**
 * Categories of characters the search contract rejects. Mirrors the BFF `SearchQueryValidator`
 * policy; the shared contract fixture `config/search-contract/search-contract-cases.json`
 * pins both sides to identical verdicts.
 */
enum class SearchInputViolation {
    /** ISO control characters (newline, tab, NUL, ...). */
    CONTROL_CHAR,

    /** Double quotes and backslashes — the only characters that break an Apicalypse literal. */
    QUOTE_OR_BACKSLASH,

    /** Zero width and bidi control characters (ZWSP, ZWNJ, LRM/RLM, bidi overrides, BOM). */
    INVISIBLE_FORMAT,

    /** More than 100 Unicode code points. */
    TOO_LONG,
}

/**
 * Client-side search input policy with the same verdicts as the BFF `SearchQueryValidator`.
 * Everything else — punctuation, emoji, ZWJ (U+200D), variation selectors — is allowed.
 */
object SearchInputPolicy {

    const val MIN_LENGTH = 1
    const val MAX_LENGTH = 100

    /**
     * Canonical query form: NFC, NBSP-like spaces collapsed to regular spaces, trimmed,
     * lowercased. Returns null when the input becomes blank after canonicalization.
     * Used for the domain query cache keys and fake data source matching.
     */
    fun canonicalize(raw: String): String? {
        val collapsed = normalizeSpaceCharacters(Normalizer.normalize(raw, Normalizer.Form.NFC))
        val trimmed = collapsed.trim()
        if (trimmed.isEmpty()) return null
        return trimmed.lowercase(Locale.ROOT)
    }

    fun validate(raw: String): SearchInputValidation {
        val normalized = Normalizer.normalize(raw, Normalizer.Form.NFC)
        var i = 0
        while (i < normalized.length) {
            val cp = normalized.codePointAt(i)
            val violation = when {
                Character.isISOControl(cp) -> SearchInputViolation.CONTROL_CHAR
                cp == '"'.code || cp == '\\'.code -> SearchInputViolation.QUOTE_OR_BACKSLASH
                isForbiddenFormatCodePoint(cp) -> SearchInputViolation.INVISIBLE_FORMAT
                else -> null
            }
            if (violation != null) {
                return SearchInputValidation.Invalid(violation)
            }
            i += Character.charCount(cp)
        }

        // The length limit applies to the canonical (NFC, collapsed, trimmed) form so that
        // decomposed-but-contract-valid titles are not rejected, and the same verdict as the BFF
        // is produced for every input. Only one code point beyond the limit is needed to make a
        // clearly-invalid input surface TOO_LONG instead of a truncated-but-valid search.
        val canonical = canonicalize(normalized)
        if (
            canonical == null &&
            normalized.codePoints().anyMatch { it == ZERO_WIDTH_JOINER }
        ) {
            return SearchInputValidation.Invalid(SearchInputViolation.INVISIBLE_FORMAT)
        }
        if (canonical != null && canonical.codePointCount(0, canonical.length) > MAX_LENGTH) {
            return SearchInputValidation.Invalid(SearchInputViolation.TOO_LONG)
        }
        return SearchInputValidation.Valid(raw)
    }

    /**
     * Same verdicts as the BFF when the call happens outside the UI path (demo data source).
     * Returns the canonical query or throws [IllegalArgumentException] like the backend does.
     */
    fun validateOrThrow(raw: String): String {
        when (val result = validate(raw)) {
            is SearchInputValidation.Invalid -> {
                throw IllegalArgumentException("Search query contains unpermitted characters (${result.violation})")
            }
            is SearchInputValidation.Valid -> Unit
        }
        val canonical = canonicalize(raw)
            ?: throw IllegalArgumentException("Search query 'q' parameter cannot be blank")
        if (canonical.codePointCount(0, canonical.length) < MIN_LENGTH) {
            throw IllegalArgumentException("Search query must be between $MIN_LENGTH and $MAX_LENGTH characters")
        }
        return canonical
    }

    private fun normalizeSpaceCharacters(value: String): String = buildString(value.length) {
        var previousWasSpace = false
        value.codePoints().forEach { cp ->
            when {
                cp == ZERO_WIDTH_JOINER -> Unit
                cp == ' '.code || Character.isSpaceChar(cp) -> {
                    if (!previousWasSpace) append(' ')
                    previousWasSpace = true
                }
                else -> {
                    appendCodePoint(cp)
                    previousWasSpace = false
                }
            }
        }
    }

    /**
     * Rejects the whole Unicode format category (zero-width, bidi controls, word joiner, ALM, BOM)
     * instead of an enumerable subset; ZWJ (U+200D) stays allowed so compound emoji remain searchable.
     * Variation selectors are combining marks, not format characters, so they pass through.
     */
    private fun isForbiddenFormatCodePoint(codePoint: Int): Boolean =
        Character.getType(codePoint) == Character.FORMAT.toInt() && codePoint != ZERO_WIDTH_JOINER

    private const val ZERO_WIDTH_JOINER = 0x200D
}
