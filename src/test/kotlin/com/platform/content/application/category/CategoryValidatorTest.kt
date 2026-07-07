package com.platform.content.application.category

import com.platform.content.domain.ValidationException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CategoryValidatorTest {

    private val validator = CategoryValidator()

    @Test
    fun `should accept valid category name`() {
        assertDoesNotThrow { validator.validate("Technology") }
    }

    @Test
    fun `should accept name with exactly 2 characters`() {
        assertDoesNotThrow { validator.validate("AI") }
    }

    @Test
    fun `should accept name with exactly 100 characters`() {
        val name = "a".repeat(100)
        assertDoesNotThrow { validator.validate(name) }
    }

    @Test
    fun `should reject blank name`() {
        val exception = assertThrows<ValidationException> { validator.validate("   ") }
        assertTrue(exception.fieldErrors.any { it.field == "name" })
    }

    @Test
    fun `should reject empty name`() {
        val exception = assertThrows<ValidationException> { validator.validate("") }
        assertTrue(exception.fieldErrors.any { it.field == "name" })
    }

    @Test
    fun `should reject name shorter than 2 characters`() {
        val exception = assertThrows<ValidationException> { validator.validate("A") }
        assertTrue(exception.fieldErrors.any { it.field == "name" })
    }

    @Test
    fun `should reject name longer than 100 characters`() {
        val name = "a".repeat(101)
        val exception = assertThrows<ValidationException> { validator.validate(name) }
        assertTrue(exception.fieldErrors.any { it.field == "name" })
    }

    @Test
    fun `should accept null description`() {
        assertDoesNotThrow { validator.validate("Technology", null) }
    }

    @Test
    fun `should accept valid description`() {
        assertDoesNotThrow { validator.validate("Technology", "A description of technology") }
    }

    @Test
    fun `should accept description with exactly 500 characters`() {
        val description = "d".repeat(500)
        assertDoesNotThrow { validator.validate("Technology", description) }
    }

    @Test
    fun `should reject description longer than 500 characters`() {
        val description = "d".repeat(501)
        val exception = assertThrows<ValidationException> {
            validator.validate("Technology", description)
        }
        assertTrue(exception.fieldErrors.any { it.field == "description" })
    }

    @Test
    fun `should collect multiple errors when both name and description are invalid`() {
        val longName = "a".repeat(101)
        val longDescription = "d".repeat(501)
        val exception = assertThrows<ValidationException> {
            validator.validate(longName, longDescription)
        }
        assertEquals(2, exception.fieldErrors.size)
        assertTrue(exception.fieldErrors.any { it.field == "name" })
        assertTrue(exception.fieldErrors.any { it.field == "description" })
    }
}
