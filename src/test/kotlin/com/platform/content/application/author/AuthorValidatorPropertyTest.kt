package com.platform.content.application.author

import com.platform.content.domain.ValidationException
import net.jqwik.api.*
import net.jqwik.api.Combinators.combine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag

/**
 * Property-based tests for AuthorValidator.
 *
 * Validates: Requirements 1.2, 1.5
 *
 * Property 1: Author field validation
 * For any string inputs for name, email, and bio: the author validation logic should accept
 * inputs where name is 1–100 characters and non-blank, email conforms to RFC 5322 format
 * and is ≤ 255 characters, and bio (if provided) is ≤ 500 characters — and reject all
 * inputs violating any of these constraints.
 */
@Tag("Feature: content-publishing-platform, Property 1: Author field validation")
class AuthorValidatorPropertyTest {

    private val validator = AuthorValidator()

    // ===== Generators =====

    /**
     * Generates valid author names: non-blank strings between 1–100 characters.
     * Uses alphanumeric and common name characters, ensuring the result is non-blank.
     */
    @Provide
    fun validNames(): Arbitrary<String> {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .withChars(' ', '-', '\'')
            .ofMinLength(1)
            .ofMaxLength(100)
            .filter { it.isNotBlank() }
    }

    /**
     * Generates valid email addresses matching RFC 5322 pattern with ≤ 255 chars.
     * Constructs emails as local@domain.tld.
     */
    @Provide
    fun validEmails(): Arbitrary<String> {
        val localPart = Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('0', '9')
            .withChars('.', '+', '_')
            .ofMinLength(1)
            .ofMaxLength(64)
            .filter { it.matches(Regex("^[a-z0-9.+_]+$")) && !it.startsWith(".") && !it.endsWith(".") }

        val domainLabel = Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('0', '9')
            .ofMinLength(1)
            .ofMaxLength(20)
            .filter { it.first().isLetterOrDigit() && it.last().isLetterOrDigit() }

        val tld = Arbitraries.of("com", "org", "net", "io", "dev", "co")

        return combine(localPart, domainLabel, tld).`as` { local, domain, t ->
            "$local@$domain.$t"
        }.filter { it.length <= 255 }
    }

    /**
     * Generates valid bios: either null or strings of ≤ 500 characters.
     */
    @Provide
    fun validBios(): Arbitrary<String?> {
        val bioString = Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withChars(' ', '.', ',', '!')
            .ofMinLength(0)
            .ofMaxLength(500)

        return Arbitraries.oneOf(
            Arbitraries.just(null as String?),
            bioString.map { it as String? }
        )
    }

    /**
     * Generates invalid names: blank, empty, or exceeding 100 characters.
     */
    @Provide
    fun invalidNames(): Arbitrary<String> {
        val blank = Arbitraries.of("", " ", "   ", "\t", "\n", "  \t  ")
        val tooLong = Arbitraries.strings()
            .withCharRange('a', 'z')
            .ofMinLength(101)
            .ofMaxLength(200)

        return Arbitraries.oneOf(blank, tooLong)
    }

    /**
     * Generates invalid emails: no @ sign, blank, or exceeding 255 characters.
     */
    @Provide
    fun invalidEmails(): Arbitrary<String> {
        val blank = Arbitraries.of("", " ", "  \t")
        val noAtSign = Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('0', '9')
            .ofMinLength(3)
            .ofMaxLength(30)
            .filter { !it.contains('@') }
        val tooLong = Arbitraries.strings()
            .withCharRange('a', 'z')
            .ofMinLength(244)
            .ofMaxLength(300)
            .map { "$it@example.com" }
            .filter { it.length > 255 }

        return Arbitraries.oneOf(blank, noAtSign, tooLong)
    }

    /**
     * Generates invalid bios: strings exceeding 500 characters.
     */
    @Provide
    fun invalidBios(): Arbitrary<String> {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .ofMinLength(501)
            .ofMaxLength(700)
    }

    // ===== Property Tests =====

    @Property(tries = 100)
    fun `valid inputs should be accepted without exception`(
        @ForAll("validNames") name: String,
        @ForAll("validEmails") email: String,
        @ForAll("validBios") bio: String?
    ) {
        assertDoesNotThrow {
            validator.validate(name, email, bio)
        }
    }

    @Property(tries = 100)
    fun `invalid name should be rejected with name field error`(
        @ForAll("invalidNames") name: String,
        @ForAll("validEmails") email: String,
        @ForAll("validBios") bio: String?
    ) {
        val ex = assertThrows(ValidationException::class.java) {
            validator.validate(name, email, bio)
        }
        assertTrue(
            ex.fieldErrors.any { it.field == "name" },
            "Expected a 'name' field error for invalid name: '$name'"
        )
    }

    @Property(tries = 100)
    fun `invalid email should be rejected with email field error`(
        @ForAll("validNames") name: String,
        @ForAll("invalidEmails") email: String,
        @ForAll("validBios") bio: String?
    ) {
        val ex = assertThrows(ValidationException::class.java) {
            validator.validate(name, email, bio)
        }
        assertTrue(
            ex.fieldErrors.any { it.field == "email" },
            "Expected an 'email' field error for invalid email: '$email'"
        )
    }

    @Property(tries = 100)
    fun `invalid bio should be rejected with bio field error`(
        @ForAll("validNames") name: String,
        @ForAll("validEmails") email: String,
        @ForAll("invalidBios") bio: String
    ) {
        val ex = assertThrows(ValidationException::class.java) {
            validator.validate(name, email, bio)
        }
        assertTrue(
            ex.fieldErrors.any { it.field == "bio" },
            "Expected a 'bio' field error for bio of length: ${bio.length}"
        )
    }
}
