package com.platform.content.application.category

import java.text.Normalizer

/**
 * Utility object for generating URL-safe slugs from category names.
 *
 * The slug generation is deterministic: the same input always produces the same output.
 * Output matches the pattern [a-z0-9-]+ and is guaranteed non-empty for any valid category name.
 */
object SlugGenerator {

    private val NON_ALPHANUMERIC = Regex("[^a-z0-9]")
    private val MULTIPLE_HYPHENS = Regex("-{2,}")

    /**
     * Generates a URL-safe slug from the given input string.
     *
     * Steps:
     * 1. Convert to lowercase
     * 2. Normalize to NFD and strip diacritical marks (accents)
     * 3. Replace non-alphanumeric characters with hyphens
     * 4. Collapse multiple consecutive hyphens into one
     * 5. Trim leading/trailing hyphens
     *
     * @param input the source string (typically a category name)
     * @return a slug matching [a-z0-9-]+, or empty string if input yields no alphanumeric content
     */
    fun generate(input: String): String {
        val normalized = Normalizer.normalize(input.lowercase(), Normalizer.Form.NFD)
        val stripped = normalized.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        val hyphenated = stripped.replace(NON_ALPHANUMERIC, "-")
        val collapsed = hyphenated.replace(MULTIPLE_HYPHENS, "-")
        return collapsed.trim('-')
    }
}
