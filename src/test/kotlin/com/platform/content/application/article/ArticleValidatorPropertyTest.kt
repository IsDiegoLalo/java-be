package com.platform.content.application.article

import com.platform.content.domain.ValidationException
import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide
import net.jqwik.api.Tag
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows

/**
 * Property-based tests for ArticleValidator.
 *
 * **Validates: Requirements 3.8**
 *
 * Property 8: For any article creation or update input, the validation logic should
 * accept inputs where title is 1–255 characters and non-blank, body is non-blank,
 * summary (if provided) is ≤ 500 characters, and tags contains ≤ 10 entries —
 * and reject all inputs violating any of these constraints.
 */
@Tag("Feature: content-publishing-platform, Property 8: Article field validation")
class ArticleValidatorPropertyTest {

    private val validator = ArticleValidator()

    // --- Generators ---

    @Provide
    fun validTitles(): Arbitrary<String> {
        return Arbitraries.integers().between(1, 255).flatMap { length ->
            Arbitraries.strings()
                .alpha()
                .ofMinLength(length)
                .ofMaxLength(length)
                .filter { it.isNotBlank() }
        }
    }

    @Provide
    fun validBodies(): Arbitrary<String> {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(1)
            .ofMaxLength(1000)
            .filter { it.isNotBlank() }
    }

    @Provide
    fun validSummaries(): Arbitrary<String?> {
        val nonNull = Arbitraries.strings()
            .alpha()
            .ofMinLength(1)
            .ofMaxLength(500)
        val nullValue = Arbitraries.just<String?>(null)
        return Arbitraries.oneOf(nonNull, nullValue)
    }

    @Provide
    fun validTagLists(): Arbitrary<List<String>> {
        return Arbitraries.integers().between(0, 10).flatMap { size ->
            Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(30)
                .list()
                .ofSize(size)
        }
    }

    @Provide
    fun invalidTitlesBlank(): Arbitrary<String> {
        return Arbitraries.of("", " ", "   ", "\t", "\n", "  \t  ")
    }

    @Provide
    fun invalidTitlesTooLong(): Arbitrary<String> {
        return Arbitraries.integers().between(256, 500).flatMap { length ->
            Arbitraries.strings()
                .alpha()
                .ofMinLength(length)
                .ofMaxLength(length)
        }
    }

    @Provide
    fun invalidBodiesBlank(): Arbitrary<String> {
        return Arbitraries.of("", " ", "   ", "\t", "\n", "  \t  ")
    }

    @Provide
    fun invalidSummariesTooLong(): Arbitrary<String> {
        return Arbitraries.integers().between(501, 800).flatMap { length ->
            Arbitraries.strings()
                .alpha()
                .ofMinLength(length)
                .ofMaxLength(length)
        }
    }

    @Provide
    fun invalidTagListsTooMany(): Arbitrary<List<String>> {
        return Arbitraries.integers().between(11, 20).flatMap { size ->
            Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(20)
                .list()
                .ofSize(size)
        }
    }

    // --- Property: Valid inputs are accepted ---

    @Property(tries = 100)
    fun `valid inputs should be accepted without exception`(
        @ForAll("validTitles") title: String,
        @ForAll("validBodies") body: String,
        @ForAll("validSummaries") summary: String?,
        @ForAll("validTagLists") tags: List<String>
    ) {
        assertDoesNotThrow {
            validator.validate(title, body, summary, tags)
        }
    }

    // --- Property: Invalid title (blank) is rejected ---

    @Property(tries = 100)
    fun `blank titles should be rejected with title field error`(
        @ForAll("invalidTitlesBlank") title: String,
        @ForAll("validBodies") body: String,
        @ForAll("validSummaries") summary: String?,
        @ForAll("validTagLists") tags: List<String>
    ) {
        val ex = assertThrows<ValidationException> {
            validator.validate(title, body, summary, tags)
        }
        assertTrue(ex.fieldErrors.any { it.field == "title" }) {
            "Expected a 'title' field error for blank title: '$title'"
        }
    }

    // --- Property: Invalid title (too long) is rejected ---

    @Property(tries = 100)
    fun `titles exceeding 255 characters should be rejected with title field error`(
        @ForAll("invalidTitlesTooLong") title: String,
        @ForAll("validBodies") body: String,
        @ForAll("validSummaries") summary: String?,
        @ForAll("validTagLists") tags: List<String>
    ) {
        val ex = assertThrows<ValidationException> {
            validator.validate(title, body, summary, tags)
        }
        assertTrue(ex.fieldErrors.any { it.field == "title" }) {
            "Expected a 'title' field error for title of length ${title.length}"
        }
    }

    // --- Property: Invalid body (blank) is rejected ---

    @Property(tries = 100)
    fun `blank bodies should be rejected with body field error`(
        @ForAll("validTitles") title: String,
        @ForAll("invalidBodiesBlank") body: String,
        @ForAll("validSummaries") summary: String?,
        @ForAll("validTagLists") tags: List<String>
    ) {
        val ex = assertThrows<ValidationException> {
            validator.validate(title, body, summary, tags)
        }
        assertTrue(ex.fieldErrors.any { it.field == "body" }) {
            "Expected a 'body' field error for blank body: '$body'"
        }
    }

    // --- Property: Invalid summary (too long) is rejected ---

    @Property(tries = 100)
    fun `summaries exceeding 500 characters should be rejected with summary field error`(
        @ForAll("validTitles") title: String,
        @ForAll("validBodies") body: String,
        @ForAll("invalidSummariesTooLong") summary: String,
        @ForAll("validTagLists") tags: List<String>
    ) {
        val ex = assertThrows<ValidationException> {
            validator.validate(title, body, summary, tags)
        }
        assertTrue(ex.fieldErrors.any { it.field == "summary" }) {
            "Expected a 'summary' field error for summary of length ${summary.length}"
        }
    }

    // --- Property: Invalid tags (too many) is rejected ---

    @Property(tries = 100)
    fun `tag lists exceeding 10 entries should be rejected with tags field error`(
        @ForAll("validTitles") title: String,
        @ForAll("validBodies") body: String,
        @ForAll("validSummaries") summary: String?,
        @ForAll("invalidTagListsTooMany") tags: List<String>
    ) {
        val ex = assertThrows<ValidationException> {
            validator.validate(title, body, summary, tags)
        }
        assertTrue(ex.fieldErrors.any { it.field == "tags" }) {
            "Expected a 'tags' field error for ${tags.size} tags"
        }
    }
}
