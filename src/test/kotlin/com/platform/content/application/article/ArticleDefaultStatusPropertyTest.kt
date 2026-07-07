package com.platform.content.application.article

import com.platform.content.domain.model.Article
import com.platform.content.domain.model.ArticleStatus
import com.platform.content.domain.model.Author
import com.platform.content.domain.model.Category
import com.platform.content.domain.port.ArticleRepository
import com.platform.content.domain.port.AuthorRepository
import com.platform.content.domain.port.CategoryRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide
import net.jqwik.api.Tag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import java.time.Instant
import java.util.UUID

/**
 * Property-based tests for new article default status.
 *
 * **Validates: Requirements 3.2**
 *
 * Property 7: For any valid article creation request (with valid title, body, author_id,
 * and category_id), the resulting article entity should always have status "draft"
 * and a null published_at timestamp.
 */
@Tag("Feature: content-publishing-platform, Property 7: New article default status")
class ArticleDefaultStatusPropertyTest {

    private val articleRepository: ArticleRepository = mockk()
    private val authorRepository: AuthorRepository = mockk()
    private val categoryRepository: CategoryRepository = mockk()
    private val articleValidator = ArticleValidator()

    private val articleService = ArticleService(
        articleRepository = articleRepository,
        authorRepository = authorRepository,
        categoryRepository = categoryRepository,
        articleValidator = articleValidator
    )

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

    // --- Property: New article always has draft status and null publishedAt ---

    @Property(tries = 100)
    fun `any valid article creation should result in draft status and null publishedAt`(
        @ForAll("validTitles") title: String,
        @ForAll("validBodies") body: String,
        @ForAll("validSummaries") summary: String?,
        @ForAll("validTagLists") tags: List<String>
    ) {
        // Arrange: mock FK checks to pass and capture saved article
        val authorId = UUID.randomUUID()
        val categoryId = UUID.randomUUID()

        every { authorRepository.findById(authorId) } returns Author(
            id = authorId,
            name = "Test Author",
            email = "author@example.com",
            bio = null,
            createdAt = Instant.now()
        )

        every { categoryRepository.findById(categoryId) } returns Category(
            id = categoryId,
            name = "Test Category",
            description = null,
            slug = "test-category"
        )

        val articleSlot = slot<Article>()
        every { articleRepository.save(capture(articleSlot)) } answers { articleSlot.captured }

        // Act
        val result = articleService.create(title, body, summary, authorId, categoryId, tags)

        // Assert: status is always Draft and publishedAt is always null
        assertEquals(ArticleStatus.Draft, result.status) {
            "Expected status Draft but got ${result.status} for title='$title'"
        }
        assertNull(result.publishedAt) {
            "Expected publishedAt to be null but got ${result.publishedAt} for title='$title'"
        }
    }
}
