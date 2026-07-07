package com.platform.content.application.category

import com.platform.content.domain.ValidationException
import net.jqwik.api.*
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows

/**
 * Property-based tests for CategoryValidator name validation.
 *
 * **Validates: Requirements 2.2**
 *
 * Property 4: Category name validation
 * For any string input for category name: the validation logic should accept names
 * that are 2–100 characters and non-blank — and reject names that are blank,
 * shorter than 2 characters, or longer than 100 characters.
 *
 * Note: Uniqueness (case-insensitive) is tested at the service level, not here.
 */
@Tag("Feature: content-publishing-platform, Property 4: Category name validation")
class CategoryValidatorPropertyTest {

    private val validator = CategoryValidator()

    /**
     * Valid names: non-blank strings between 2 and 100 characters.
     * The validator should accept all such names without throwing.
     */
    @Property(tries = 100)
    fun `should accept any non-blank name between 2 and 100 characters`(
        @ForAll("validCategoryNames") name: String
    ) {
        assertDoesNotThrow { validator.validate(name) }
    }

    /**
     * Invalid names: blank or whitespace-only strings.
     * The validator should reject all such names with a field error on "name".
     */
    @Property(tries = 100)
    fun `should reject any blank or whitespace-only name`(
        @ForAll("blankNames") name: String
    ) {
        val exception = assertThrows<ValidationException> { validator.validate(name) }
        assertTrue(exception.fieldErrors.any { it.field == "name" })
    }

    /**
     * Invalid names: single character (length < 2).
     * The validator should reject all such names with a field error on "name".
     */
    @Property(tries = 100)
    fun `should reject any name shorter than 2 characters`(
        @ForAll("tooShortNames") name: String
    ) {
        val exception = assertThrows<ValidationException> { validator.validate(name) }
        assertTrue(exception.fieldErrors.any { it.field == "name" })
    }

    /**
     * Invalid names: strings longer than 100 characters.
     * The validator should reject all such names with a field error on "name".
     */
    @Property(tries = 100)
    fun `should reject any name longer than 100 characters`(
        @ForAll("tooLongNames") name: String
    ) {
        val exception = assertThrows<ValidationException> { validator.validate(name) }
        assertTrue(exception.fieldErrors.any { it.field == "name" })
    }

    // --- Generators ---

    @Provide
    fun validCategoryNames(): Arbitrary<String> {
        // Generate non-blank strings between 2 and 100 characters using printable non-whitespace ASCII
        return Arbitraries.strings()
            .withCharRange('!', '~')
            .ofMinLength(2)
            .ofMaxLength(100)
    }

    @Provide
    fun blankNames(): Arbitrary<String> {
        // Generate strings that are blank: empty or whitespace-only
        return Arbitraries.oneOf(
            Arbitraries.just(""),
            Arbitraries.integers().between(1, 50).map { len -> " ".repeat(len) },
            Arbitraries.integers().between(1, 50).map { len -> "\t".repeat(len) },
            Arbitraries.integers().between(1, 50).map { len ->
                val whitespaceChars = listOf(' ', '\t', '\n', '\r')
                (1..len).map { whitespaceChars.random() }.joinToString("")
            }
        )
    }

    @Provide
    fun tooShortNames(): Arbitrary<String> {
        // Generate non-blank single character strings (length exactly 1)
        return Arbitraries.chars()
            .filter { !it.isWhitespace() }
            .map { it.toString() }
    }

    @Provide
    fun tooLongNames(): Arbitrary<String> {
        // Generate non-blank strings longer than 100 characters
        return Arbitraries.integers().between(101, 300).flatMap { length ->
            Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofLength(length)
        }
    }
}
