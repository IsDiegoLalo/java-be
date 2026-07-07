package com.platform.content.application.author

import com.platform.content.domain.ConflictException
import com.platform.content.domain.model.Author
import com.platform.content.domain.port.ArticleRepository
import com.platform.content.domain.port.AuthorRepository
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import net.jqwik.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import java.time.Instant
import java.util.UUID

/**
 * Property-based tests for duplicate email uniqueness enforcement in AuthorService.
 *
 * **Validates: Requirements 1.6**
 *
 * Property 3: Duplicate email uniqueness
 * For any two authors, if the second author is created or updated with an email address
 * that already belongs to another author, the operation should always be rejected with a
 * duplicate email error.
 */
@Tag("Feature: content-publishing-platform, Property 3: Duplicate email uniqueness")
class AuthorEmailUniquenessPropertyTest {

    private val authorRepository: AuthorRepository = mockk()
    private val articleRepository: ArticleRepository = mockk()
    private val authorValidator: AuthorValidator = mockk()

    private val authorService = AuthorService(authorRepository, articleRepository, authorValidator)

    // ===== Generators =====

    /**
     * Generates arbitrary valid email addresses for testing uniqueness logic.
     */
    @Provide
    fun emails(): Arbitrary<String> {
        val localPart = Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('0', '9')
            .withChars('.')
            .ofMinLength(1)
            .ofMaxLength(30)
            .filter { it.matches(Regex("^[a-z0-9.]+$")) && !it.startsWith(".") && !it.endsWith(".") }

        val domainLabel = Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('0', '9')
            .ofMinLength(2)
            .ofMaxLength(15)
            .filter { it.first().isLetterOrDigit() && it.last().isLetterOrDigit() }

        val tld = Arbitraries.of("com", "org", "net", "io", "dev")

        return Combinators.combine(localPart, domainLabel, tld).`as` { local, domain, t ->
            "$local@$domain.$t"
        }.filter { it.length <= 255 }
    }

    /**
     * Generates arbitrary valid author names for testing.
     */
    @Provide
    fun names(): Arbitrary<String> {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withChars(' ', '-')
            .ofMinLength(1)
            .ofMaxLength(100)
            .filter { it.isNotBlank() }
    }

    /**
     * Generates optional bio strings.
     */
    @Provide
    fun bios(): Arbitrary<String?> {
        return Arbitraries.oneOf(
            Arbitraries.just(null as String?),
            Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1)
                .ofMaxLength(100)
                .map { it as String? }
        )
    }

    // ===== Property Tests =====

    @Property(tries = 100)
    fun `create should always reject when email already belongs to another author`(
        @ForAll("emails") duplicateEmail: String,
        @ForAll("names") name: String,
        @ForAll("bios") bio: String?
    ) {
        // Arrange: An existing author with a different UUID already has this email
        val existingAuthor = Author(
            id = UUID.randomUUID(),
            name = "Existing Author",
            email = duplicateEmail,
            bio = null,
            createdAt = Instant.now()
        )

        justRun { authorValidator.validate(name, duplicateEmail, bio) }
        every { authorRepository.findByEmail(duplicateEmail) } returns existingAuthor

        // Act & Assert: Creating with a duplicate email must always throw ConflictException
        val ex = assertThrows(ConflictException::class.java) {
            authorService.create(name, duplicateEmail, bio)
        }

        assertEquals("Author", ex.entityType)
        assertTrue(
            ex.conflictReason.contains("already in use"),
            "Expected conflict reason to contain 'already in use', got: '${ex.conflictReason}'"
        )
    }

    @Property(tries = 100)
    fun `update should always reject when email already belongs to a different author`(
        @ForAll("emails") duplicateEmail: String,
        @ForAll("names") name: String,
        @ForAll("bios") bio: String?
    ) {
        // Arrange: The author being updated exists
        val updatingAuthorId = UUID.randomUUID()
        val updatingAuthor = Author(
            id = updatingAuthorId,
            name = "Original Name",
            email = "original@example.com",
            bio = null,
            createdAt = Instant.now()
        )

        // A different author already owns the target email
        val otherAuthor = Author(
            id = UUID.randomUUID(), // Different UUID - this is the key condition
            name = "Other Author",
            email = duplicateEmail,
            bio = null,
            createdAt = Instant.now()
        )

        every { authorRepository.findById(updatingAuthorId) } returns updatingAuthor
        justRun { authorValidator.validate(name, duplicateEmail, bio) }
        every { authorRepository.findByEmail(duplicateEmail) } returns otherAuthor

        // Act & Assert: Updating with an email owned by a different author must always throw
        val ex = assertThrows(ConflictException::class.java) {
            authorService.update(updatingAuthorId, name, duplicateEmail, bio)
        }

        assertEquals("Author", ex.entityType)
        assertTrue(
            ex.conflictReason.contains("already in use"),
            "Expected conflict reason to contain 'already in use', got: '${ex.conflictReason}'"
        )
    }

    @Property(tries = 100)
    fun `update should allow keeping same email for the same author`(
        @ForAll("emails") email: String,
        @ForAll("names") name: String,
        @ForAll("bios") bio: String?
    ) {
        // Arrange: The author being updated already owns this email (self-update scenario)
        val authorId = UUID.randomUUID()
        val existingAuthor = Author(
            id = authorId,
            name = "Original Name",
            email = email,
            bio = null,
            createdAt = Instant.now()
        )

        every { authorRepository.findById(authorId) } returns existingAuthor
        justRun { authorValidator.validate(name, email, bio) }
        every { authorRepository.findByEmail(email) } returns existingAuthor // Same author
        val authorSlot = slot<Author>()
        every { authorRepository.save(capture(authorSlot)) } answers { authorSlot.captured }

        // Act & Assert: Updating with own email should succeed (no conflict)
        val result = authorService.update(authorId, name, email, bio)

        assertEquals(authorId, result.id)
        assertEquals(email, result.email)
    }
}
