package com.platform.content.infrastructure.analytics

import com.platform.content.domain.model.Article
import com.platform.content.domain.model.ArticleStatus
import com.platform.content.domain.model.InteractionType
import com.platform.content.domain.port.ArticleFilter
import com.platform.content.domain.port.ArticleRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID

/**
 * Integration tests for MongoDB engagement analytics operations using Testcontainers.
 * Tests the MongoEngagementWriteAdapter and MongoEngagementReadAdapter against
 * a real MongoDB 7 instance.
 *
 * Requirements validated: 6.1, 6.2, 7.1, 7.2
 */
@DataMongoTest
@Testcontainers
@ActiveProfiles("test")
class MongoEngagementIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val mongoContainer: MongoDBContainer = MongoDBContainer("mongo:7.0")

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.mongodb.uri") { mongoContainer.replicaSetUrl }
        }
    }

    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    private lateinit var writeAdapter: MongoEngagementWriteAdapter
    private lateinit var readAdapter: MongoEngagementReadAdapter
    private lateinit var articleRepository: ArticleRepository

    @BeforeEach
    fun setUp() {
        mongoTemplate.remove(Query(), EngagementDocument::class.java)
        writeAdapter = MongoEngagementWriteAdapter(mongoTemplate)
        articleRepository = mockk()
        readAdapter = MongoEngagementReadAdapter(mongoTemplate, articleRepository)
    }

    // ===========================
    // Page View Recording (Req 6.1)
    // ===========================

    @Nested
    @DisplayName("Page View Recording")
    inner class PageViewRecordingTests {

        @Test
        @DisplayName("should create engagement record on first page view")
        fun `first page view creates document with count 1`() {
            val articleId = UUID.randomUUID()

            writeAdapter.recordPageView(articleId)

            val record = readAdapter.getByArticleId(articleId)
            assertEquals(1L, record.pageViews)
        }

        @Test
        @DisplayName("should increment page views on multiple views")
        fun `multiple page views increment counter correctly`() {
            val articleId = UUID.randomUUID()

            repeat(5) { writeAdapter.recordPageView(articleId) }

            val record = readAdapter.getByArticleId(articleId)
            assertEquals(5L, record.pageViews)
        }

        @Test
        @DisplayName("should track page views independently per article")
        fun `page views are tracked independently per article`() {
            val articleId1 = UUID.randomUUID()
            val articleId2 = UUID.randomUUID()

            repeat(3) { writeAdapter.recordPageView(articleId1) }
            repeat(7) { writeAdapter.recordPageView(articleId2) }

            assertEquals(3L, readAdapter.getByArticleId(articleId1).pageViews)
            assertEquals(7L, readAdapter.getByArticleId(articleId2).pageViews)
        }
    }

    // ===========================
    // Read Time Recording (Req 6.2)
    // ===========================

    @Nested
    @DisplayName("Read Time Recording")
    inner class ReadTimeRecordingTests {

        @Test
        @DisplayName("should record single read time correctly")
        fun `single read time sets average equal to value`() {
            val articleId = UUID.randomUUID()

            writeAdapter.recordReadTime(articleId, 120)

            val record = readAdapter.getByArticleId(articleId)
            assertEquals(120.0, record.averageReadTimeSeconds, 0.01)
        }

        @Test
        @DisplayName("should compute running average for multiple read times")
        fun `multiple read times compute correct average`() {
            val articleId = UUID.randomUUID()

            writeAdapter.recordReadTime(articleId, 60)
            writeAdapter.recordReadTime(articleId, 120)
            writeAdapter.recordReadTime(articleId, 180)

            val record = readAdapter.getByArticleId(articleId)
            // Average of 60, 120, 180 = 120.0
            assertEquals(120.0, record.averageReadTimeSeconds, 0.01)
        }

        @Test
        @DisplayName("should discard read time values less than 1 second")
        fun `read time below 1 second is discarded`() {
            val articleId = UUID.randomUUID()

            writeAdapter.recordReadTime(articleId, 100)
            writeAdapter.recordReadTime(articleId, 0)
            writeAdapter.recordReadTime(articleId, -5)

            val record = readAdapter.getByArticleId(articleId)
            assertEquals(100.0, record.averageReadTimeSeconds, 0.01)
        }

        @Test
        @DisplayName("should discard read time values greater than 3600 seconds")
        fun `read time above 3600 seconds is discarded`() {
            val articleId = UUID.randomUUID()

            writeAdapter.recordReadTime(articleId, 200)
            writeAdapter.recordReadTime(articleId, 3601)
            writeAdapter.recordReadTime(articleId, 5000)

            val record = readAdapter.getByArticleId(articleId)
            assertEquals(200.0, record.averageReadTimeSeconds, 0.01)
        }

        @Test
        @DisplayName("should accept boundary values 1 and 3600")
        fun `boundary values 1 and 3600 are accepted`() {
            val articleId = UUID.randomUUID()

            writeAdapter.recordReadTime(articleId, 1)
            writeAdapter.recordReadTime(articleId, 3600)

            val record = readAdapter.getByArticleId(articleId)
            // Average of 1 and 3600 = 1800.5
            assertEquals(1800.5, record.averageReadTimeSeconds, 0.01)
        }
    }

    // ===========================
    // Interaction Recording (Req 6.2)
    // ===========================

    @Nested
    @DisplayName("Interaction Recording")
    inner class InteractionRecordingTests {

        @Test
        @DisplayName("should increment like count")
        fun `record like increments likes counter`() {
            val articleId = UUID.randomUUID()

            repeat(3) { writeAdapter.recordInteraction(articleId, InteractionType.LIKE) }

            val record = readAdapter.getByArticleId(articleId)
            assertEquals(3L, record.interactions.likes)
            assertEquals(0L, record.interactions.shares)
            assertEquals(0L, record.interactions.comments)
        }

        @Test
        @DisplayName("should increment share count")
        fun `record share increments shares counter`() {
            val articleId = UUID.randomUUID()

            repeat(2) { writeAdapter.recordInteraction(articleId, InteractionType.SHARE) }

            val record = readAdapter.getByArticleId(articleId)
            assertEquals(0L, record.interactions.likes)
            assertEquals(2L, record.interactions.shares)
            assertEquals(0L, record.interactions.comments)
        }

        @Test
        @DisplayName("should increment comment count")
        fun `record comment increments comments counter`() {
            val articleId = UUID.randomUUID()

            repeat(4) { writeAdapter.recordInteraction(articleId, InteractionType.COMMENT) }

            val record = readAdapter.getByArticleId(articleId)
            assertEquals(0L, record.interactions.likes)
            assertEquals(0L, record.interactions.shares)
            assertEquals(4L, record.interactions.comments)
        }

        @Test
        @DisplayName("should track all interaction types independently")
        fun `all interaction types tracked independently`() {
            val articleId = UUID.randomUUID()

            repeat(5) { writeAdapter.recordInteraction(articleId, InteractionType.LIKE) }
            repeat(3) { writeAdapter.recordInteraction(articleId, InteractionType.SHARE) }
            repeat(8) { writeAdapter.recordInteraction(articleId, InteractionType.COMMENT) }

            val record = readAdapter.getByArticleId(articleId)
            assertEquals(5L, record.interactions.likes)
            assertEquals(3L, record.interactions.shares)
            assertEquals(8L, record.interactions.comments)
        }
    }

    // ===========================
    // Get Engagement by Article ID (Req 7.1)
    // ===========================

    @Nested
    @DisplayName("Get Engagement by Article ID")
    inner class GetByArticleIdTests {

        @Test
        @DisplayName("should return complete engagement record with all fields")
        fun `returns all fields for existing engagement`() {
            val articleId = UUID.randomUUID()

            repeat(10) { writeAdapter.recordPageView(articleId) }
            writeAdapter.recordReadTime(articleId, 60)
            writeAdapter.recordReadTime(articleId, 120)
            repeat(3) { writeAdapter.recordInteraction(articleId, InteractionType.LIKE) }
            repeat(2) { writeAdapter.recordInteraction(articleId, InteractionType.SHARE) }
            writeAdapter.recordInteraction(articleId, InteractionType.COMMENT)

            val record = readAdapter.getByArticleId(articleId)

            assertAll(
                { assertEquals(articleId, record.articleId) },
                { assertEquals(10L, record.pageViews) },
                { assertEquals(90.0, record.averageReadTimeSeconds, 0.01) },
                { assertEquals(3L, record.interactions.likes) },
                { assertEquals(2L, record.interactions.shares) },
                { assertEquals(1L, record.interactions.comments) }
            )
        }

        @Test
        @DisplayName("should return zeroed record for non-existent article engagement")
        fun `returns zeroed record when no engagement exists`() {
            val articleId = UUID.randomUUID()

            val record = readAdapter.getByArticleId(articleId)

            assertAll(
                { assertEquals(articleId, record.articleId) },
                { assertEquals(0L, record.pageViews) },
                { assertEquals(0.0, record.averageReadTimeSeconds, 0.01) },
                { assertEquals(0L, record.interactions.likes) },
                { assertEquals(0L, record.interactions.shares) },
                { assertEquals(0L, record.interactions.comments) }
            )
        }
    }

    // ===========================
    // Author Aggregation (Req 7.2)
    // ===========================

    @Nested
    @DisplayName("Author Aggregation")
    inner class AuthorAggregationTests {

        @Test
        @DisplayName("should aggregate metrics across multiple articles by author")
        fun `aggregates page views, interactions, and weighted read time`() {
            val authorId = UUID.randomUUID()
            val articleId1 = UUID.randomUUID()
            val articleId2 = UUID.randomUUID()
            val articleId3 = UUID.randomUUID()

            // Article 1: 10 views, avg read time 60s
            repeat(10) { writeAdapter.recordPageView(articleId1) }
            writeAdapter.recordReadTime(articleId1, 60)

            // Article 2: 20 views, avg read time 120s
            repeat(20) { writeAdapter.recordPageView(articleId2) }
            writeAdapter.recordReadTime(articleId2, 120)

            // Article 3: 5 views, avg read time 30s
            repeat(5) { writeAdapter.recordPageView(articleId3) }
            writeAdapter.recordReadTime(articleId3, 30)

            // Interactions
            repeat(4) { writeAdapter.recordInteraction(articleId1, InteractionType.LIKE) }
            repeat(2) { writeAdapter.recordInteraction(articleId2, InteractionType.SHARE) }
            repeat(6) { writeAdapter.recordInteraction(articleId3, InteractionType.COMMENT) }

            // Mock ArticleRepository to return these article IDs for the author
            val articles = listOf(articleId1, articleId2, articleId3).map { id ->
                Article(
                    id = id,
                    title = "Article",
                    body = "Body",
                    summary = null,
                    authorId = authorId,
                    categoryId = UUID.randomUUID(),
                    tags = emptyList(),
                    status = ArticleStatus.Published,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                    publishedAt = Instant.now()
                )
            }
            every {
                articleRepository.findAll(
                    filter = ArticleFilter(authorId = authorId),
                    pageable = Pageable.unpaged()
                )
            } returns PageImpl(articles)

            val aggregated = readAdapter.getAggregatedByAuthorId(authorId)

            assertAll(
                { assertEquals(authorId, aggregated.authorId) },
                // Total page views: 10 + 20 + 5 = 35
                { assertEquals(35L, aggregated.totalPageViews) },
                // Total interactions: likes=4, shares=2, comments=6
                { assertEquals(4L, aggregated.totalInteractions.likes) },
                { assertEquals(2L, aggregated.totalInteractions.shares) },
                { assertEquals(6L, aggregated.totalInteractions.comments) },
                // Weighted avg read time: (60*10 + 120*20 + 30*5) / (10+20+5) = 3150/35 = 90.0
                { assertEquals(90.0, aggregated.averageReadTimeSeconds, 0.5) }
            )
        }

        @Test
        @DisplayName("should return zeroed metrics when author has no articles")
        fun `returns zeroed metrics for author with no articles`() {
            val authorId = UUID.randomUUID()

            every {
                articleRepository.findAll(
                    filter = ArticleFilter(authorId = authorId),
                    pageable = Pageable.unpaged()
                )
            } returns PageImpl(emptyList())

            val aggregated = readAdapter.getAggregatedByAuthorId(authorId)

            assertAll(
                { assertEquals(authorId, aggregated.authorId) },
                { assertEquals(0L, aggregated.totalPageViews) },
                { assertEquals(0.0, aggregated.averageReadTimeSeconds, 0.01) },
                { assertEquals(0L, aggregated.totalInteractions.likes) },
                { assertEquals(0L, aggregated.totalInteractions.shares) },
                { assertEquals(0L, aggregated.totalInteractions.comments) }
            )
        }

        @Test
        @DisplayName("should return zeroed metrics when articles have no engagement")
        fun `returns zeroed metrics when articles exist but no engagement recorded`() {
            val authorId = UUID.randomUUID()
            val articleId1 = UUID.randomUUID()
            val articleId2 = UUID.randomUUID()

            val articles = listOf(articleId1, articleId2).map { id ->
                Article(
                    id = id,
                    title = "Article",
                    body = "Body",
                    summary = null,
                    authorId = authorId,
                    categoryId = UUID.randomUUID(),
                    tags = emptyList(),
                    status = ArticleStatus.Published,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                    publishedAt = Instant.now()
                )
            }
            every {
                articleRepository.findAll(
                    filter = ArticleFilter(authorId = authorId),
                    pageable = Pageable.unpaged()
                )
            } returns PageImpl(articles)

            val aggregated = readAdapter.getAggregatedByAuthorId(authorId)

            assertAll(
                { assertEquals(authorId, aggregated.authorId) },
                { assertEquals(0L, aggregated.totalPageViews) },
                { assertEquals(0.0, aggregated.averageReadTimeSeconds, 0.01) },
                { assertEquals(0L, aggregated.totalInteractions.likes) },
                { assertEquals(0L, aggregated.totalInteractions.shares) },
                { assertEquals(0L, aggregated.totalInteractions.comments) }
            )
        }

        @Test
        @DisplayName("should handle single article aggregation correctly")
        fun `single article aggregation equals article metrics`() {
            val authorId = UUID.randomUUID()
            val articleId = UUID.randomUUID()

            repeat(15) { writeAdapter.recordPageView(articleId) }
            writeAdapter.recordReadTime(articleId, 90)
            writeAdapter.recordReadTime(articleId, 110)
            repeat(7) { writeAdapter.recordInteraction(articleId, InteractionType.LIKE) }
            repeat(3) { writeAdapter.recordInteraction(articleId, InteractionType.COMMENT) }

            val articles = listOf(
                Article(
                    id = articleId,
                    title = "Single Article",
                    body = "Body",
                    summary = null,
                    authorId = authorId,
                    categoryId = UUID.randomUUID(),
                    tags = emptyList(),
                    status = ArticleStatus.Published,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                    publishedAt = Instant.now()
                )
            )
            every {
                articleRepository.findAll(
                    filter = ArticleFilter(authorId = authorId),
                    pageable = Pageable.unpaged()
                )
            } returns PageImpl(articles)

            val aggregated = readAdapter.getAggregatedByAuthorId(authorId)

            assertAll(
                { assertEquals(15L, aggregated.totalPageViews) },
                // Average of 90 and 110 = 100.0
                { assertEquals(100.0, aggregated.averageReadTimeSeconds, 0.5) },
                { assertEquals(7L, aggregated.totalInteractions.likes) },
                { assertEquals(0L, aggregated.totalInteractions.shares) },
                { assertEquals(3L, aggregated.totalInteractions.comments) }
            )
        }
    }
}
