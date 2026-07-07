package com.platform.content.application.analytics

import com.platform.content.domain.EntityNotFoundException
import com.platform.content.domain.model.AggregatedEngagement
import com.platform.content.domain.model.Article
import com.platform.content.domain.model.ArticleStatus
import com.platform.content.domain.model.EngagementRecord
import com.platform.content.domain.model.InteractionCounts
import com.platform.content.domain.model.InteractionType
import com.platform.content.domain.port.ArticleRepository
import com.platform.content.domain.port.EngagementReadPort
import com.platform.content.domain.port.EngagementWritePort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

class EngagementServiceTest {

    private val engagementWritePort: EngagementWritePort = mockk(relaxed = true)
    private val engagementReadPort: EngagementReadPort = mockk()
    private val articleRepository: ArticleRepository = mockk()

    private lateinit var service: EngagementService

    private val existingArticleId = UUID.randomUUID()
    private val nonExistentArticleId = UUID.randomUUID()
    private val authorId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        service = EngagementService(engagementWritePort, engagementReadPort, articleRepository)

        every { articleRepository.findById(existingArticleId) } returns Article(
            id = existingArticleId,
            title = "Test Article",
            body = "Test body",
            summary = null,
            authorId = authorId,
            categoryId = UUID.randomUUID(),
            tags = emptyList(),
            status = ArticleStatus.Draft,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            publishedAt = null
        )
        every { articleRepository.findById(nonExistentArticleId) } returns null
    }

    // --- Page view recording ---

    @Test
    fun `should record page view for existing article`() {
        service.recordPageView(existingArticleId)

        verify(exactly = 1) { engagementWritePort.recordPageView(existingArticleId) }
    }

    @Test
    fun `should discard page view for non-existent article`() {
        service.recordPageView(nonExistentArticleId)

        verify(exactly = 0) { engagementWritePort.recordPageView(any()) }
    }

    // --- Read time recording ---

    @Test
    fun `should record valid read time for existing article`() {
        service.recordReadTime(existingArticleId, 120)

        verify(exactly = 1) { engagementWritePort.recordReadTime(existingArticleId, 120) }
    }

    @Test
    fun `should record minimum valid read time`() {
        service.recordReadTime(existingArticleId, 1)

        verify(exactly = 1) { engagementWritePort.recordReadTime(existingArticleId, 1) }
    }

    @Test
    fun `should record maximum valid read time`() {
        service.recordReadTime(existingArticleId, 3600)

        verify(exactly = 1) { engagementWritePort.recordReadTime(existingArticleId, 3600) }
    }

    @Test
    fun `should discard read time below minimum`() {
        service.recordReadTime(existingArticleId, 0)

        verify(exactly = 0) { engagementWritePort.recordReadTime(any(), any()) }
    }

    @Test
    fun `should discard read time above maximum`() {
        service.recordReadTime(existingArticleId, 3601)

        verify(exactly = 0) { engagementWritePort.recordReadTime(any(), any()) }
    }

    @Test
    fun `should discard negative read time`() {
        service.recordReadTime(existingArticleId, -5)

        verify(exactly = 0) { engagementWritePort.recordReadTime(any(), any()) }
    }

    @Test
    fun `should discard read time for non-existent article`() {
        service.recordReadTime(nonExistentArticleId, 120)

        verify(exactly = 0) { engagementWritePort.recordReadTime(any(), any()) }
    }

    // --- Interaction recording ---

    @Test
    fun `should record like interaction for existing article`() {
        service.recordInteraction(existingArticleId, InteractionType.LIKE)

        verify(exactly = 1) { engagementWritePort.recordInteraction(existingArticleId, InteractionType.LIKE) }
    }

    @Test
    fun `should record share interaction for existing article`() {
        service.recordInteraction(existingArticleId, InteractionType.SHARE)

        verify(exactly = 1) { engagementWritePort.recordInteraction(existingArticleId, InteractionType.SHARE) }
    }

    @Test
    fun `should record comment interaction for existing article`() {
        service.recordInteraction(existingArticleId, InteractionType.COMMENT)

        verify(exactly = 1) { engagementWritePort.recordInteraction(existingArticleId, InteractionType.COMMENT) }
    }

    @Test
    fun `should discard interaction for non-existent article`() {
        service.recordInteraction(nonExistentArticleId, InteractionType.LIKE)

        verify(exactly = 0) { engagementWritePort.recordInteraction(any(), any()) }
    }

    // --- Article engagement retrieval ---

    @Test
    fun `should return engagement for existing article`() {
        val expected = EngagementRecord(
            articleId = existingArticleId,
            pageViews = 42,
            averageReadTimeSeconds = 180.0,
            interactions = InteractionCounts(likes = 10, shares = 5, comments = 3)
        )
        every { engagementReadPort.getByArticleId(existingArticleId) } returns expected

        val result = service.getArticleEngagement(existingArticleId)

        assertEquals(expected, result)
    }

    @Test
    fun `should throw EntityNotFoundException for non-existent article retrieval`() {
        val ex = assertThrows<EntityNotFoundException> {
            service.getArticleEngagement(nonExistentArticleId)
        }
        assertEquals("Article", ex.entityType)
        assertEquals(nonExistentArticleId, ex.entityId)
    }

    // --- Author engagement retrieval ---

    @Test
    fun `should return aggregated engagement for author`() {
        val expected = AggregatedEngagement(
            authorId = authorId,
            totalPageViews = 100,
            averageReadTimeSeconds = 200.0,
            totalInteractions = InteractionCounts(likes = 20, shares = 10, comments = 5)
        )
        every { engagementReadPort.getAggregatedByAuthorId(authorId) } returns expected

        val result = service.getAuthorEngagement(authorId)

        assertEquals(expected, result)
    }

    @Test
    fun `should return zeroed metrics for author with no articles`() {
        val zeroed = AggregatedEngagement(
            authorId = authorId,
            totalPageViews = 0,
            averageReadTimeSeconds = 0.0,
            totalInteractions = InteractionCounts(likes = 0, shares = 0, comments = 0)
        )
        every { engagementReadPort.getAggregatedByAuthorId(authorId) } returns zeroed

        val result = service.getAuthorEngagement(authorId)

        assertEquals(0L, result.totalPageViews)
        assertEquals(0.0, result.averageReadTimeSeconds)
        assertEquals(0L, result.totalInteractions.likes)
        assertEquals(0L, result.totalInteractions.shares)
        assertEquals(0L, result.totalInteractions.comments)
    }
}
