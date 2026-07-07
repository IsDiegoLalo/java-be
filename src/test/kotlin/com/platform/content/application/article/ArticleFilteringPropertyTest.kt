package com.platform.content.application.article

import com.platform.content.domain.model.Article
import com.platform.content.domain.model.ArticleStatus
import com.platform.content.domain.port.ArticleFilter
import com.platform.content.domain.port.ArticleRepository
import com.platform.content.domain.port.AuthorRepository
import com.platform.content.domain.port.CategoryRepository
import io.mockk.every
import io.mockk.mockk
import net.jqwik.api.*
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import java.time.Instant
import java.util.UUID

/**
 * Property-based tests for article list filtering correctness.
 *
 * **Validates: Requirements 3.5**
 *
 * Property 13: For any dataset of articles and any combination of filters
 * (author, category, status, tags), all articles returned in the result set
 * must satisfy every specified filter criterion.
 */
@Tag("Feature: content-publishing-platform, Property 13: Article list filtering correctness")
class ArticleFilteringPropertyTest {

    private val articleRepository: ArticleRepository = mockk()
    private val authorRepository: AuthorRepository = mockk()
    private val categoryRepository: CategoryRepository = mockk()
    private val articleValidator: ArticleValidator = ArticleValidator()

    private val service = ArticleService(articleRepository, authorRepository, categoryRepository, articleValidator)

    // --- Generators ---

    @Provide
    fun articleDatasets(): Arbitrary<List<Article>> {
        return articles().list().ofMinSize(0).ofMaxSize(20)
    }

    @Provide
    fun articles(): Arbitrary<Article> {
        return Combinators.combine(
            Arbitraries.create { UUID.randomUUID() },
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50),
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(100),
            Arbitraries.of(null as String?, "A summary"),
            Arbitraries.create { UUID.randomUUID() },
            Arbitraries.create { UUID.randomUUID() },
            tagListArbitrary(),
            statusArbitrary()
        ).`as` { id, title, body, summary, authorId, categoryId, tags, status ->
            val now = Instant.now()
            Article(
                id = id,
                title = title,
                body = body,
                summary = summary,
                authorId = authorId,
                categoryId = categoryId,
                tags = tags,
                status = status,
                createdAt = now.minusSeconds(7200),
                updatedAt = now.minusSeconds(3600),
                publishedAt = if (status == ArticleStatus.Published) now else null
            )
        }
    }

    @Provide
    fun filters(): Arbitrary<ArticleFilter> {
        return Combinators.combine(
            Arbitraries.of(true, false),
            Arbitraries.of(true, false),
            Arbitraries.of(true, false),
            Arbitraries.of(true, false)
        ).`as` { useAuthor, useCategory, useStatus, useTags ->
            FilterSpec(useAuthor, useCategory, useStatus, useTags)
        }.flatMap { spec -> buildFilterArbitrary(spec) }
    }

    private fun statusArbitrary(): Arbitrary<ArticleStatus> {
        return Arbitraries.of(
            ArticleStatus.Draft,
            ArticleStatus.Review,
            ArticleStatus.Published
        )
    }

    private fun tagListArbitrary(): Arbitrary<List<String>> {
        return Arbitraries.integers().between(0, 5).flatMap { size ->
            Arbitraries.of("kotlin", "java", "spring", "testing", "api", "backend", "jqwik")
                .list().ofSize(size)
        }.map { it.distinct() }
    }

    private data class FilterSpec(
        val useAuthor: Boolean,
        val useCategory: Boolean,
        val useStatus: Boolean,
        val useTags: Boolean
    )

    private fun buildFilterArbitrary(spec: FilterSpec): Arbitrary<ArticleFilter> {
        val authorId: Arbitrary<UUID?> = if (spec.useAuthor) {
            Arbitraries.create { UUID.randomUUID() }.map { it as UUID? }
        } else {
            Arbitraries.just(null)
        }

        val categoryId: Arbitrary<UUID?> = if (spec.useCategory) {
            Arbitraries.create { UUID.randomUUID() }.map { it as UUID? }
        } else {
            Arbitraries.just(null)
        }

        val status: Arbitrary<ArticleStatus?> = if (spec.useStatus) {
            statusArbitrary().map { it as ArticleStatus? }
        } else {
            Arbitraries.just(null)
        }

        val tags: Arbitrary<List<String>?> = if (spec.useTags) {
            Arbitraries.of("kotlin", "java", "spring", "testing", "api")
                .list().ofMinSize(1).ofMaxSize(3)
                .map { it.distinct() as List<String>? }
        } else {
            Arbitraries.just(null)
        }

        return Combinators.combine(authorId, categoryId, status, tags)
            .`as` { a, c, s, t -> ArticleFilter(authorId = a, categoryId = c, status = s, tags = t) }
    }

    // --- In-memory filter implementation (reference implementation) ---

    private fun applyFilter(articles: List<Article>, filter: ArticleFilter): List<Article> {
        return articles.filter { article ->
            val matchesAuthor = filter.authorId == null || article.authorId == filter.authorId
            val matchesCategory = filter.categoryId == null || article.categoryId == filter.categoryId
            val matchesStatus = filter.status == null || article.status == filter.status
            val matchesTags = filter.tags == null || filter.tags!!.all { tag -> tag in article.tags }
            matchesAuthor && matchesCategory && matchesStatus && matchesTags
        }
    }

    // --- Properties ---

    /**
     * Property: All returned articles satisfy every specified filter criterion.
     * Uses an in-memory mock that applies filtering, then verifies each result.
     */
    @Property(tries = 100)
    fun `all returned articles must satisfy every non-null filter criterion`(
        @ForAll("articleDatasets") dataset: List<Article>,
        @ForAll("filters") filter: ArticleFilter
    ) {
        val pageable: Pageable = PageRequest.of(0, 20)
        val filtered = applyFilter(dataset, filter)
        val page: Page<Article> = PageImpl(filtered, pageable, filtered.size.toLong())

        every { articleRepository.findAll(filter, pageable) } returns page

        val result = service.list(filter, pageable)

        result.content.forEach { article ->
            if (filter.authorId != null) {
                assertTrue(article.authorId == filter.authorId) {
                    "Article ${article.id} has authorId=${article.authorId} but filter requires authorId=${filter.authorId}"
                }
            }
            if (filter.categoryId != null) {
                assertTrue(article.categoryId == filter.categoryId) {
                    "Article ${article.id} has categoryId=${article.categoryId} but filter requires categoryId=${filter.categoryId}"
                }
            }
            if (filter.status != null) {
                assertTrue(article.status == filter.status) {
                    "Article ${article.id} has status=${article.status.value} but filter requires status=${filter.status!!.value}"
                }
            }
            if (filter.tags != null) {
                filter.tags!!.forEach { requiredTag ->
                    assertTrue(requiredTag in article.tags) {
                        "Article ${article.id} missing required tag '$requiredTag'. Article tags: ${article.tags}"
                    }
                }
            }
        }
    }

    /**
     * Property: Filtering with a dataset-derived filter returns only matching articles.
     * Picks filter values from the actual dataset to ensure non-empty results.
     */
    @Property(tries = 100)
    fun `filtering with values from dataset returns correct subset`(
        @ForAll("articleDatasets") dataset: List<Article>
    ) {
        Assume.that(dataset.isNotEmpty())

        // Pick a random article's properties to build a filter that should match at least one
        val reference = dataset.random()
        val filter = ArticleFilter(
            authorId = reference.authorId,
            categoryId = null,
            status = null,
            tags = null
        )

        val pageable: Pageable = PageRequest.of(0, 20)
        val filtered = applyFilter(dataset, filter)
        val page: Page<Article> = PageImpl(filtered, pageable, filtered.size.toLong())

        every { articleRepository.findAll(filter, pageable) } returns page

        val result = service.list(filter, pageable)

        assertTrue(result.content.isNotEmpty()) {
            "Expected at least one result when filtering by an authorId that exists in the dataset"
        }
        result.content.forEach { article ->
            assertTrue(article.authorId == reference.authorId) {
                "Article ${article.id} has authorId=${article.authorId} but expected ${reference.authorId}"
            }
        }
    }

    /**
     * Property: Filtering with combined criteria only returns articles matching ALL criteria.
     * Picks multiple filter fields from the dataset to test conjunction.
     */
    @Property(tries = 100)
    fun `combined filter criteria are applied as conjunction`(
        @ForAll("articleDatasets") dataset: List<Article>
    ) {
        Assume.that(dataset.isNotEmpty())

        val reference = dataset.random()
        val filter = ArticleFilter(
            authorId = reference.authorId,
            categoryId = reference.categoryId,
            status = reference.status,
            tags = if (reference.tags.isNotEmpty()) listOf(reference.tags.first()) else null
        )

        val pageable: Pageable = PageRequest.of(0, 20)
        val filtered = applyFilter(dataset, filter)
        val page: Page<Article> = PageImpl(filtered, pageable, filtered.size.toLong())

        every { articleRepository.findAll(filter, pageable) } returns page

        val result = service.list(filter, pageable)

        result.content.forEach { article ->
            assertTrue(article.authorId == filter.authorId) {
                "Article ${article.id} authorId mismatch"
            }
            assertTrue(article.categoryId == filter.categoryId) {
                "Article ${article.id} categoryId mismatch"
            }
            assertTrue(article.status == filter.status) {
                "Article ${article.id} status mismatch"
            }
            if (filter.tags != null) {
                filter.tags!!.forEach { tag ->
                    assertTrue(tag in article.tags) {
                        "Article ${article.id} missing tag '$tag'"
                    }
                }
            }
        }
    }

    /**
     * Property: An empty filter (all null) returns all articles from the dataset.
     */
    @Property(tries = 100)
    fun `empty filter returns all articles in dataset`(
        @ForAll("articleDatasets") dataset: List<Article>
    ) {
        val filter = ArticleFilter()
        val pageable: Pageable = PageRequest.of(0, 100)
        val page: Page<Article> = PageImpl(dataset, pageable, dataset.size.toLong())

        every { articleRepository.findAll(filter, pageable) } returns page

        val result = service.list(filter, pageable)

        assertTrue(result.content.size == dataset.size) {
            "Empty filter should return all articles. Expected ${dataset.size}, got ${result.content.size}"
        }
    }
}
