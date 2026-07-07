package com.platform.content.api.dto

import com.platform.content.domain.model.AggregatedEngagement
import com.platform.content.domain.model.Article
import com.platform.content.domain.model.ArticleStatus
import com.platform.content.domain.model.Author
import com.platform.content.domain.model.Category
import com.platform.content.domain.model.EngagementRecord
import com.platform.content.domain.model.InteractionCounts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class DtoMapperTest {

    @Test
    fun `AuthorResponse fromDomain maps all fields correctly`() {
        val author = Author(
            id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            name = "John Doe",
            email = "john@example.com",
            bio = "A writer",
            createdAt = Instant.parse("2024-01-15T10:30:00.123Z")
        )

        val response = AuthorResponse.fromDomain(author)

        assertEquals(author.id, response.id)
        assertEquals("John Doe", response.name)
        assertEquals("john@example.com", response.email)
        assertEquals("A writer", response.bio)
        assertEquals("2024-01-15T10:30:00.123Z", response.createdAt)
    }

    @Test
    fun `AuthorResponse fromDomain handles null bio`() {
        val author = Author(
            id = UUID.randomUUID(),
            name = "Jane",
            email = "jane@example.com",
            bio = null,
            createdAt = Instant.parse("2024-06-01T12:00:00.000Z")
        )

        val response = AuthorResponse.fromDomain(author)

        assertNull(response.bio)
    }

    @Test
    fun `CategoryResponse fromDomain maps all fields correctly`() {
        val category = Category(
            id = UUID.fromString("22222222-2222-2222-2222-222222222222"),
            name = "Technology",
            description = "Tech articles",
            slug = "technology"
        )

        val response = CategoryResponse.fromDomain(category)

        assertEquals(category.id, response.id)
        assertEquals("Technology", response.name)
        assertEquals("Tech articles", response.description)
        assertEquals("technology", response.slug)
    }

    @Test
    fun `CategoryResponse fromDomain handles null description`() {
        val category = Category(
            id = UUID.randomUUID(),
            name = "Science",
            description = null,
            slug = "science"
        )

        val response = CategoryResponse.fromDomain(category)

        assertNull(response.description)
    }

    @Test
    fun `ArticleResponse fromDomain maps all fields correctly`() {
        val article = Article(
            id = UUID.fromString("33333333-3333-3333-3333-333333333333"),
            title = "Test Article",
            body = "Article body content",
            summary = "A summary",
            authorId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            categoryId = UUID.fromString("22222222-2222-2222-2222-222222222222"),
            tags = listOf("kotlin", "spring"),
            status = ArticleStatus.Published,
            createdAt = Instant.parse("2024-01-10T08:00:00.000Z"),
            updatedAt = Instant.parse("2024-01-15T14:30:00.500Z"),
            publishedAt = Instant.parse("2024-01-15T14:30:00.500Z")
        )

        val response = ArticleResponse.fromDomain(article)

        assertEquals(article.id, response.id)
        assertEquals("Test Article", response.title)
        assertEquals("Article body content", response.body)
        assertEquals("A summary", response.summary)
        assertEquals(article.authorId, response.authorId)
        assertEquals(article.categoryId, response.categoryId)
        assertEquals(listOf("kotlin", "spring"), response.tags)
        assertEquals("published", response.status)
        assertEquals("2024-01-10T08:00:00.000Z", response.createdAt)
        assertEquals("2024-01-15T14:30:00.500Z", response.updatedAt)
        assertEquals("2024-01-15T14:30:00.500Z", response.publishedAt)
    }

    @Test
    fun `ArticleResponse fromDomain handles draft status and null fields`() {
        val article = Article(
            id = UUID.randomUUID(),
            title = "Draft Article",
            body = "Body",
            summary = null,
            authorId = UUID.randomUUID(),
            categoryId = UUID.randomUUID(),
            tags = emptyList(),
            status = ArticleStatus.Draft,
            createdAt = Instant.parse("2024-03-01T00:00:00.000Z"),
            updatedAt = Instant.parse("2024-03-01T00:00:00.000Z"),
            publishedAt = null
        )

        val response = ArticleResponse.fromDomain(article)

        assertEquals("draft", response.status)
        assertNull(response.summary)
        assertNull(response.publishedAt)
        assertEquals(emptyList<String>(), response.tags)
    }

    @Test
    fun `ArticleResponse fromDomain maps review status correctly`() {
        val article = Article(
            id = UUID.randomUUID(),
            title = "Review Article",
            body = "Body",
            summary = null,
            authorId = UUID.randomUUID(),
            categoryId = UUID.randomUUID(),
            tags = listOf("tag1"),
            status = ArticleStatus.Review,
            createdAt = Instant.parse("2024-03-01T00:00:00.000Z"),
            updatedAt = Instant.parse("2024-03-02T10:00:00.000Z"),
            publishedAt = null
        )

        val response = ArticleResponse.fromDomain(article)

        assertEquals("review", response.status)
    }

    @Test
    fun `ArticleResponse fromDomain formats dates in ISO 8601 with milliseconds`() {
        val article = Article(
            id = UUID.randomUUID(),
            title = "Date Test",
            body = "Body",
            summary = null,
            authorId = UUID.randomUUID(),
            categoryId = UUID.randomUUID(),
            tags = emptyList(),
            status = ArticleStatus.Draft,
            createdAt = Instant.parse("2024-06-15T09:05:03.007Z"),
            updatedAt = Instant.parse("2024-06-15T09:05:03.007Z"),
            publishedAt = null
        )

        val response = ArticleResponse.fromDomain(article)

        assertEquals("2024-06-15T09:05:03.007Z", response.createdAt)
    }

    @Test
    fun `EngagementResponse fromDomain maps all fields correctly`() {
        val record = EngagementRecord(
            articleId = UUID.fromString("44444444-4444-4444-4444-444444444444"),
            pageViews = 150,
            averageReadTimeSeconds = 45.5,
            interactions = InteractionCounts(likes = 10, shares = 5, comments = 3)
        )

        val response = EngagementResponse.fromDomain(record)

        assertEquals(record.articleId, response.articleId)
        assertEquals(150L, response.pageViews)
        assertEquals(45.5, response.averageReadTimeSeconds)
        assertEquals(10L, response.likes)
        assertEquals(5L, response.shares)
        assertEquals(3L, response.comments)
    }

    @Test
    fun `EngagementResponse fromDomain handles zero values`() {
        val record = EngagementRecord(
            articleId = UUID.randomUUID(),
            pageViews = 0,
            averageReadTimeSeconds = 0.0,
            interactions = InteractionCounts(likes = 0, shares = 0, comments = 0)
        )

        val response = EngagementResponse.fromDomain(record)

        assertEquals(0L, response.pageViews)
        assertEquals(0.0, response.averageReadTimeSeconds)
        assertEquals(0L, response.likes)
        assertEquals(0L, response.shares)
        assertEquals(0L, response.comments)
    }

    @Test
    fun `AggregatedEngagementResponse fromDomain maps all fields correctly`() {
        val aggregated = AggregatedEngagement(
            authorId = UUID.fromString("55555555-5555-5555-5555-555555555555"),
            totalPageViews = 5000,
            averageReadTimeSeconds = 120.75,
            totalInteractions = InteractionCounts(likes = 200, shares = 50, comments = 30)
        )

        val response = AggregatedEngagementResponse.fromDomain(aggregated)

        assertEquals(aggregated.authorId, response.authorId)
        assertEquals(5000L, response.totalPageViews)
        assertEquals(120.75, response.averageReadTimeSeconds)
        assertEquals(200L, response.totalLikes)
        assertEquals(50L, response.totalShares)
        assertEquals(30L, response.totalComments)
    }

    @Test
    fun `PageResponse of calculates totalPages correctly`() {
        val page = PageResponse.of(
            content = listOf("a", "b", "c"),
            page = 0,
            size = 3,
            totalElements = 10
        )

        assertEquals(3, page.content.size)
        assertEquals(0, page.page)
        assertEquals(3, page.size)
        assertEquals(10L, page.totalElements)
        assertEquals(4, page.totalPages) // ceil(10/3) = 4
    }

    @Test
    fun `PageResponse of handles exact division`() {
        val page = PageResponse.of(
            content = listOf("a", "b"),
            page = 0,
            size = 5,
            totalElements = 10
        )

        assertEquals(2, page.totalPages) // 10/5 = 2
    }

    @Test
    fun `PageResponse of handles zero size`() {
        val page = PageResponse.of(
            content = emptyList<String>(),
            page = 0,
            size = 0,
            totalElements = 0
        )

        assertEquals(0, page.totalPages)
    }

    @Test
    fun `PageResponse of handles empty content with remaining elements`() {
        val page = PageResponse.of(
            content = emptyList<String>(),
            page = 5,
            size = 20,
            totalElements = 100
        )

        assertEquals(5, page.totalPages) // ceil(100/20) = 5
    }
}
