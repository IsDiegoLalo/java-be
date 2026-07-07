package com.platform.content.application.category

import net.jqwik.api.*
import org.junit.jupiter.api.Tag
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property-based tests for SlugGenerator invariants.
 *
 * **Validates: Requirements 2.5**
 */
@Tag("Feature: content-publishing-platform, Property 5: Slug generation invariants")
class SlugGeneratorPropertyTest {

    /**
     * Provides valid category names: non-blank strings between 2–100 characters
     * containing at least one alphanumeric character.
     */
    @Provide
    fun validCategoryNames(): Arbitrary<String> {
        val chars = ('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf(' ', '-', '_', '.', ',', '!', '&', '(', ')', '+')
        return Arbitraries.strings()
            .ofMinLength(2)
            .ofMaxLength(100)
            .withChars(*chars.toCharArray())
            .filter { name ->
                name.isNotBlank() && name.any { it.isLetterOrDigit() }
            }
    }

    @Property(tries = 100)
    fun `slug is entirely lowercase for any valid category name`(
        @ForAll("validCategoryNames") name: String
    ) {
        val slug = SlugGenerator.generate(name)
        assertEquals(slug, slug.lowercase(), "Slug should be entirely lowercase for input: '$name'")
    }

    @Property(tries = 100)
    fun `slug contains only lowercase alphanumeric characters and hyphens`(
        @ForAll("validCategoryNames") name: String
    ) {
        val slug = SlugGenerator.generate(name)
        assertTrue(
            slug.matches(Regex("[a-z0-9-]+")),
            "Slug '$slug' should match [a-z0-9-]+ for input: '$name'"
        )
    }

    @Property(tries = 100)
    fun `slug is non-empty for any valid category name`(
        @ForAll("validCategoryNames") name: String
    ) {
        val slug = SlugGenerator.generate(name)
        assertTrue(slug.isNotEmpty(), "Slug should be non-empty for input: '$name'")
    }

    @Property(tries = 100)
    fun `slug generation is deterministic`(
        @ForAll("validCategoryNames") name: String
    ) {
        val slug1 = SlugGenerator.generate(name)
        val slug2 = SlugGenerator.generate(name)
        assertEquals(slug1, slug2, "Slug generation should be deterministic for input: '$name'")
    }

    @Property(tries = 100)
    fun `slug has no leading or trailing hyphens`(
        @ForAll("validCategoryNames") name: String
    ) {
        val slug = SlugGenerator.generate(name)
        assertTrue(
            !slug.startsWith('-') && !slug.endsWith('-'),
            "Slug '$slug' should not have leading or trailing hyphens for input: '$name'"
        )
    }

    @Property(tries = 100)
    fun `slug has no consecutive hyphens`(
        @ForAll("validCategoryNames") name: String
    ) {
        val slug = SlugGenerator.generate(name)
        assertTrue(
            !slug.contains("--"),
            "Slug '$slug' should not contain consecutive hyphens for input: '$name'"
        )
    }
}
