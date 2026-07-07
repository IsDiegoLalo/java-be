package com.platform.content.application.author

import com.platform.content.domain.ConflictException
import com.platform.content.domain.model.Author
import com.platform.content.domain.port.ArticleRepository
import com.platform.content.domain.port.AuthorRepository
import io.mockk.every
import io.mockk.mockk
import net.jqwik.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import java.time.Instant
import java.util.UUID

/**
 * Property-based test for Author deletion guard.
 *
 * Validates: Requirements 1.3
 *
 * Property 2: Author deletion guard
 * For any author who has one or more articles in any status (draft, review, or published),
 * attempting to delete that author should always be rejected with an error indicating
 * associated articles exist.
 */
@Tag("Feature: content-publishing-platform, Property 2: Author deletion guard")
class AuthorDeletionGuardPropertyTest {

    // ===== Generators =====

    /**
     * Generates random Author objects with arbitrary valid field values.
     */
    @Provide
    fun authorsWithArticles(): Arbitrary<Author> {
        val names = Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withChars(' ', '-')
            .ofMinLength(1)
            .ofMaxLength(100)
            .filter { it.isNotBlank() }

        val emails = Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('0', '9')
            .ofMinLength(3)
            .ofMaxLength(20)
            .map { "$it@example.com" }

        val bios = Arbitraries.strings()
            .withCharRange('a', 'z')
            .ofMinLength(0)
            .ofMaxLength(500)
            .injectNull(0.3)

        return Combinators.combine(names, emails, bios).`as` { name, email, bio ->
            Author(
                id = UUID.randomUUID(),
                name = name,
                email = email,
                bio = bio,
                createdAt = Instant.now()
            )
        }
    }

    // ===== Property Tests =====

    /**
     * For any author who has associated articles, calling delete should always
     * throw a ConflictException with entityType "Author" and reason containing
     * "associated articles".
     */
    @Property(tries = 100)
    fun `deletion is always rejected when author has associated articles`(
        @ForAll("authorsWithArticles") author: Author
    ) {
        // Arrange: mock repositories with abstractions (DIP)
        val authorRepository: AuthorRepository = mockk()
        val articleRepository: ArticleRepository = mockk()
        val authorValidator: AuthorValidator = mockk()

        val service = AuthorService(authorRepository, articleRepository, authorValidator)

        // The author exists in the repository
        every { authorRepository.findById(author.id) } returns author
        // The author has associated articles (simulates any article status scenario)
        every { articleRepository.existsByAuthorId(author.id) } returns true

        // Act & Assert: deletion must throw ConflictException
        val exception = assertThrows(ConflictException::class.java) {
            service.delete(author.id)
        }

        // Verify exception details match expected contract
        assertEquals("Author", exception.entityType)
        assertTrue(
            exception.conflictReason.contains("associated articles"),
            "Expected conflict reason to contain 'associated articles', but was: '${exception.conflictReason}'"
        )
    }
}
