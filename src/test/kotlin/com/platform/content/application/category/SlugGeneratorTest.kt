package com.platform.content.application.category

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SlugGeneratorTest {

    @Test
    fun `should convert simple name to lowercase slug`() {
        assertEquals("technology", SlugGenerator.generate("Technology"))
    }

    @Test
    fun `should replace spaces with hyphens`() {
        assertEquals("web-development", SlugGenerator.generate("Web Development"))
    }

    @Test
    fun `should handle multiple spaces`() {
        assertEquals("web-development", SlugGenerator.generate("Web   Development"))
    }

    @Test
    fun `should strip diacritical marks`() {
        assertEquals("cafe", SlugGenerator.generate("Café"))
    }

    @Test
    fun `should handle accented characters`() {
        assertEquals("uber-fahrer", SlugGenerator.generate("Über Fahrer"))
    }

    @Test
    fun `should replace special characters with hyphens`() {
        assertEquals("c-programming", SlugGenerator.generate("C++ Programming"))
    }

    @Test
    fun `should collapse multiple hyphens`() {
        assertEquals("a-b", SlugGenerator.generate("a---b"))
    }

    @Test
    fun `should trim leading and trailing hyphens`() {
        assertEquals("hello", SlugGenerator.generate("--hello--"))
    }

    @Test
    fun `should produce only lowercase alphanumeric and hyphens`() {
        val slug = SlugGenerator.generate("Hello, World! @2024")
        assertTrue(slug.matches(Regex("[a-z0-9-]+")))
    }

    @Test
    fun `should be deterministic`() {
        val input = "Science & Technology"
        val slug1 = SlugGenerator.generate(input)
        val slug2 = SlugGenerator.generate(input)
        assertEquals(slug1, slug2)
    }

    @Test
    fun `should handle numbers in name`() {
        assertEquals("web-3-0", SlugGenerator.generate("Web 3.0"))
    }

    @Test
    fun `should handle all lowercase input`() {
        assertEquals("already-lowercase", SlugGenerator.generate("already lowercase"))
    }

    @Test
    fun `should handle all uppercase input`() {
        assertEquals("all-caps", SlugGenerator.generate("ALL CAPS"))
    }

    @Test
    fun `should produce non-empty slug for valid name`() {
        val slug = SlugGenerator.generate("AI")
        assertTrue(slug.isNotEmpty())
    }
}
