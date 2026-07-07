package com.platform.content.application.article

import com.platform.content.domain.ConflictException
import com.platform.content.domain.model.Article
import com.platform.content.domain.model.ArticleStatus
import com.platform.content.domain.port.ArticleRepository
import com.platform.content.domain.port.AuthorRepository
import com.platform.content.domain.port.CategoryRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.Combinators
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide
import net.jqwik.api.Tag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

/**
 * Property-based tests for published article deletion guard.
 *
 * **Validates: Requirements 3.10**
 *
 * Property 9: For any article in "published" status, attempting to delete that article
 * should always be rejected with an error indicating published articles cannot be deleted.
 */
@Tag("Feature: content-publishing-platform, Property 9: Published article deletion guard")
class ArticleDeletionGuardPropertyTest {

    private val articleRepository: ArticleRepository = mockk()
    private val authorRepository: AuthorRepository = mockk()
    private val categoryRepository: CategoryRepository = mockk()
    private val articleValidator: ArticleValidator = ArticleValidator()

    private val service = ArticleService(articleRepository, authorRepository, categoryRepository, articleValidator)

    // --- Generators ---

    @Provide
    fun articleIds(): Arbitrary<UUID> {
        return Arbitraries.create { UUID.randomUUID() }
    }

    @Provide
    fun titles(): Arbitrary<String> {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(1)
            .ofMaxLength(255)
            .filter { it.isNotBlank() }
    }

    @Provide
    fun bodies(): Arbitrary<String> {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(1)
            .ofMaxLength(500)
            .filter { it.isNotBlank() }
    }

    @Provide
    fun summaries(): Arbitrary<String?> {
        val nonNull = Arbitraries.strings()
            .alpha()
            .ofMinLength(1)
            .ofMaxLength(500)
        val nullValue = Arbitraries.just<String?>(null)
        return Arbitraries.oneOf(nonNull, nullValue)
    }

    @Provide
    fun tagLists(): Arbitrary<List<String>> {
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
    fun publishedArticles(): Arbitrary<Article> {
        return Combinators.combine(
            articleIds(),
            titles(),
            bodies(),
            summaries(),
            Arbitraries.create { UUID.randomUUID() },
            Arbitraries.create { UUID.randomUUID() },
            tagLists()
        ).`as` { id, title, body, summary, authorId, categoryId, tags ->
            val now = Instant.now()
            Article(
                id = id,
                title = title,
                body = body,
                summary = summary,
                authorId = authorId,
                categoryId = categoryId,
                tags = tags,
                status = ArticleStatus.Published,
                createdAt = now.minusSeconds(7200),
                updatedAt = now.minusSeconds(3600),
                publishedAt = now
            )
        }
    }

    // --- Property: Deletion of published article is always rejected ---

    @Property(tries = 100)
    fun `deleting a published article should always throw ConflictException`(
        @ForAll("publishedArticles") article: Article
    ) {
        every { articleRepository.findById(article.id) } returns article

        val ex = assertThrows<ConflictException> {
            service.delete(article.id)
        }

        assertEquals("Article", ex.entityType)
        assertEquals("published articles cannot be deleted", ex.conflictReason)
    }

    @Property(tries = 100)
    fun `deleting a published article should never invoke deleteById on repository`(
        @ForAll("publishedArticles") article: Article
    ) {
        every { articleRepository.findById(article.id) } returns article

        try {
            service.delete(article.id)
        } catch (_: ConflictException) {
            // Expected
        }

        verify(exactly = 0) { articleRepository.deleteById(article.id) }
    }
}
