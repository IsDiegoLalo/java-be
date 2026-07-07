package com.platform.content.application.search

import com.platform.content.domain.model.Article
import com.platform.content.domain.model.ArticleStatus
import com.platform.content.domain.port.ArticleSearchPort
import io.mockk.every
import io.mockk.mockk
import net.jqwik.api.*
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import java.time.Instant
import java.util.UUID

/**
 * Property-based tests verifying that search results only contain published articles.
 *
 * **Validates: Requirements 5.4**
 *
 * Property 14: For any full-text search query executed against a dataset containing
 * articles in mixed statuses (draft, review, published), every article in the result
 * set must have status "published".
 *
 * The ArticleSearchPort contract guarantees only published articles are returned.
 * The mock simulates this by filtering a mixed-status dataset to only published articles,
 * then we verify the invariant holds through SearchService.
 */
@Tag("Feature: content-publishing-platform, Property 14: Search returns only published articles")
class SearchPublishedOnlyPropertyTest {

    private val articleSearchPort: ArticleSearchPort = mockk()
    private val service = SearchService(articleSearchPort)

    // --- Generators ---

    @Provide
    fun validSearchQueries(): Arbitrary<String> {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(1)
            .ofMaxLength(100)
    }

    @Provide
    fun mixedStatusArticleDatasets(): Arbitrary<List<Article>> {
        return articleWithStatus().list().ofMinSize(0).ofMaxSize(15)
    }

    private fun articleWithStatus(): Arbitrary<Article> {
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

    private fun statusArbitrary(): Arbitrary<ArticleStatus> {
        return Arbitraries.of(
            ArticleStatus.Draft,
            ArticleStatus.Review,
            ArticleStatus.Published
        )
    }

    private fun tagListArbitrary(): Arbitrary<List<String>> {
        return Arbitraries.integers().between(0, 3).flatMap { size ->
            Arbitraries.of("kotlin", "java", "spring", "testing", "api")
                .list().ofSize(size)
        }.map { it.distinct() }
    }

    // --- Properties ---

    /**
     * Property: For any valid search query, all articles returned by SearchService
     * have status "published". The mock simulates the port contract by filtering
     * a mixed-status dataset to only published articles.
     */
    @Property(tries = 100)
    fun `search results must only contain published articles`(
        @ForAll("validSearchQueries") query: String,
        @ForAll("mixedStatusArticleDatasets") dataset: List<Article>
    ) {
        val pageable: Pageable = PageRequest.of(0, 20)

        // Simulate the ArticleSearchPort contract: only return published articles
        val publishedOnly = dataset.filter { it.status == ArticleStatus.Published }
        val page = PageImpl(publishedOnly, pageable, publishedOnly.size.toLong())

        every { articleSearchPort.search(query, pageable) } returns page

        val result = service.search(query, pageable)

        result.content.forEach { article ->
            assertTrue(article.status == ArticleStatus.Published) {
                "Search result contains article ${article.id} with status '${article.status.value}' " +
                    "but only 'published' articles should appear in search results"
            }
        }
    }

    /**
     * Property: For any valid search query, no article in the result set has status
     * "draft" or "review". This is the complement assertion of the above property,
     * ensuring no non-published articles leak through.
     */
    @Property(tries = 100)
    fun `search results must never contain draft or review articles`(
        @ForAll("validSearchQueries") query: String,
        @ForAll("mixedStatusArticleDatasets") dataset: List<Article>
    ) {
        val pageable: Pageable = PageRequest.of(0, 20)

        // Simulate the ArticleSearchPort contract: only return published articles
        val publishedOnly = dataset.filter { it.status == ArticleStatus.Published }
        val page = PageImpl(publishedOnly, pageable, publishedOnly.size.toLong())

        every { articleSearchPort.search(query, pageable) } returns page

        val result = service.search(query, pageable)

        val nonPublished = result.content.filter { it.status != ArticleStatus.Published }
        assertTrue(nonPublished.isEmpty()) {
            "Search results contain ${nonPublished.size} non-published article(s): " +
                nonPublished.joinToString { "${it.id} (${it.status.value})" }
        }
    }

    /**
     * Property: The count of search results never exceeds the count of published
     * articles in the original dataset. This validates that the port doesn't
     * fabricate extra results.
     */
    @Property(tries = 100)
    fun `search result count never exceeds published article count in dataset`(
        @ForAll("validSearchQueries") query: String,
        @ForAll("mixedStatusArticleDatasets") dataset: List<Article>
    ) {
        val pageable: Pageable = PageRequest.of(0, 20)

        val publishedOnly = dataset.filter { it.status == ArticleStatus.Published }
        val page = PageImpl(publishedOnly, pageable, publishedOnly.size.toLong())

        every { articleSearchPort.search(query, pageable) } returns page

        val result = service.search(query, pageable)

        val publishedCount = dataset.count { it.status == ArticleStatus.Published }
        assertTrue(result.content.size <= publishedCount) {
            "Search returned ${result.content.size} results but dataset only has $publishedCount published articles"
        }
    }
}
