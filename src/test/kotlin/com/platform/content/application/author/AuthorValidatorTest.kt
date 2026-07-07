package com.platform.content.application.author

import com.platform.content.domain.ValidationException
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AuthorValidatorTest {

    private val validator = AuthorValidator()

    // --- Valid inputs ---

    @Test
    fun `should accept valid author with all fields`() {
        assertDoesNotThrow {
            validator.validate("John Doe", "john@example.com", "A short bio")
        }
    }

    @Test
    fun `should accept valid author with null bio`() {
        assertDoesNotThrow {
            validator.validate("Jane", "jane@example.org", null)
        }
    }

    @Test
    fun `should accept name with exactly 1 character`() {
        assertDoesNotThrow {
            validator.validate("A", "a@b.com", null)
        }
    }

    @Test
    fun `should accept name with exactly 100 characters`() {
        val name = "a".repeat(100)
        assertDoesNotThrow {
            validator.validate(name, "user@domain.com", null)
        }
    }

    @Test
    fun `should accept email with exactly 255 characters`() {
        val local = "a".repeat(243)
        val email = "$local@example.com" // 243 + 1 + 11 = 255
        assertDoesNotThrow {
            validator.validate("Name", email, null)
        }
    }

    @Test
    fun `should accept bio with exactly 500 characters`() {
        val bio = "x".repeat(500)
        assertDoesNotThrow {
            validator.validate("Name", "user@test.com", bio)
        }
    }

    // --- Name validation ---

    @Test
    fun `should reject blank name`() {
        val ex = assertThrows<ValidationException> {
            validator.validate("   ", "user@test.com", null)
        }
        assertTrue(ex.fieldErrors.any { it.field == "name" && it.message.contains("blank") })
    }

    @Test
    fun `should reject empty name`() {
        val ex = assertThrows<ValidationException> {
            validator.validate("", "user@test.com", null)
        }
        assertTrue(ex.fieldErrors.any { it.field == "name" && it.message.contains("blank") })
    }

    @Test
    fun `should reject name exceeding 100 characters`() {
        val name = "a".repeat(101)
        val ex = assertThrows<ValidationException> {
            validator.validate(name, "user@test.com", null)
        }
        assertTrue(ex.fieldErrors.any { it.field == "name" && it.message.contains("100") })
    }

    // --- Email validation ---

    @Test
    fun `should reject blank email`() {
        val ex = assertThrows<ValidationException> {
            validator.validate("Name", "   ", null)
        }
        assertTrue(ex.fieldErrors.any { it.field == "email" && it.message.contains("blank") })
    }

    @Test
    fun `should reject email exceeding 255 characters`() {
        val local = "a".repeat(244)
        val email = "$local@example.com" // 244 + 1 + 11 = 256
        val ex = assertThrows<ValidationException> {
            validator.validate("Name", email, null)
        }
        assertTrue(ex.fieldErrors.any { it.field == "email" && it.message.contains("255") })
    }

    @Test
    fun `should reject email without at sign`() {
        val ex = assertThrows<ValidationException> {
            validator.validate("Name", "not-an-email", null)
        }
        assertTrue(ex.fieldErrors.any { it.field == "email" && it.message.contains("RFC 5322") })
    }

    @Test
    fun `should reject email with no domain`() {
        val ex = assertThrows<ValidationException> {
            validator.validate("Name", "user@", null)
        }
        assertTrue(ex.fieldErrors.any { it.field == "email" })
    }

    @Test
    fun `should reject email with no local part`() {
        val ex = assertThrows<ValidationException> {
            validator.validate("Name", "@domain.com", null)
        }
        assertTrue(ex.fieldErrors.any { it.field == "email" })
    }

    // --- Bio validation ---

    @Test
    fun `should reject bio exceeding 500 characters`() {
        val bio = "x".repeat(501)
        val ex = assertThrows<ValidationException> {
            validator.validate("Name", "user@test.com", bio)
        }
        assertTrue(ex.fieldErrors.any { it.field == "bio" && it.message.contains("500") })
    }

    // --- Multiple errors ---

    @Test
    fun `should collect multiple field errors`() {
        val ex = assertThrows<ValidationException> {
            validator.validate("", "invalid", "x".repeat(501))
        }
        assertEquals(3, ex.fieldErrors.size)
        assertTrue(ex.fieldErrors.any { it.field == "name" })
        assertTrue(ex.fieldErrors.any { it.field == "email" })
        assertTrue(ex.fieldErrors.any { it.field == "bio" })
    }
}
