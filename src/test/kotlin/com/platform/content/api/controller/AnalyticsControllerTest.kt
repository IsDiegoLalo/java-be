package com.platform.content.api.controller

import com.platform.content.application.analytics.EngagementService
import com.platform.content.domain.EntityNotFoundException
import com.platform.content.domain.model.AggregatedEngagement
import com.platform.content.domain.model.EngagementRecord
import com.platform.content.domain.model.InteractionCounts
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import java.util.UUID

class AnalyticsControllerTest {

    private lateinit var engagementService: EngagementService
    private lateinit var controller: AnalyticsController

    @BeforeEach
    fun setUp() {
        engagementService = mockk()
        controller = AnalyticsController(engagementService)
    }

    @Test
    fun `getArticleMetrics should return 200 with engagement response`() {
        val articleId = UUID.randomUUID()
        val record = EngagementRecord(
            articleId = articleId,
            pageViews = 150L,
            averageReadTimeSeconds = 45.5,
            interactions = InteractionCounts(likes = 10L, shares = 3L, comments = 5L)
        )
        every { engagementService.getArticleEngagement(articleId) } returns record

        val response = controller.getArticleMetrics(articleId)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals(articleId, body.articleId)
        assertEquals(150L, body.pageViews)
        assertEquals(45.5, body.averageReadTimeSeconds)
        assertEquals(10L, body.likes)
        assertEquals(3L, body.shares)
        assertEquals(5L, body.comments)
        verify { engagementService.getArticleEngagement(articleId) }
    }

    @Test
    fun `getArticleMetrics should return zeroed metrics for article with no engagement`() {
        val articleId = UUID.randomUUID()
        val record = EngagementRecord(
            articleId = articleId,
            pageViews = 0L,
            averageReadTimeSeconds = 0.0,
            interactions = InteractionCounts(likes = 0L, shares = 0L, comments = 0L)
        )
        every { engagementService.getArticleEngagement(articleId) } returns record

        val response = controller.getArticleMetrics(articleId)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals(0L, body.pageViews)
        assertEquals(0.0, body.averageReadTimeSeconds)
        assertEquals(0L, body.likes)
        assertEquals(0L, body.shares)
        assertEquals(0L, body.comments)
    }

    @Test
    fun `getArticleMetrics should propagate EntityNotFoundException for non-existent article`() {
        val articleId = UUID.randomUUID()
        every { engagementService.getArticleEngagement(articleId) } throws
            EntityNotFoundException("Article", articleId)

        assertThrows<EntityNotFoundException> {
            controller.getArticleMetrics(articleId)
        }
    }

    @Test
    fun `getAuthorMetrics should return 200 with aggregated engagement response`() {
        val authorId = UUID.randomUUID()
        val aggregated = AggregatedEngagement(
            authorId = authorId,
            totalPageViews = 1200L,
            averageReadTimeSeconds = 62.3,
            totalInteractions = InteractionCounts(likes = 80L, shares = 25L, comments = 40L)
        )
        every { engagementService.getAuthorEngagement(authorId) } returns aggregated

        val response = controller.getAuthorMetrics(authorId)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals(authorId, body.authorId)
        assertEquals(1200L, body.totalPageViews)
        assertEquals(62.3, body.averageReadTimeSeconds)
        assertEquals(80L, body.totalLikes)
        assertEquals(25L, body.totalShares)
        assertEquals(40L, body.totalComments)
        verify { engagementService.getAuthorEngagement(authorId) }
    }

    @Test
    fun `getAuthorMetrics should return zeroed metrics for author with no articles`() {
        val authorId = UUID.randomUUID()
        val aggregated = AggregatedEngagement(
            authorId = authorId,
            totalPageViews = 0L,
            averageReadTimeSeconds = 0.0,
            totalInteractions = InteractionCounts(likes = 0L, shares = 0L, comments = 0L)
        )
        every { engagementService.getAuthorEngagement(authorId) } returns aggregated

        val response = controller.getAuthorMetrics(authorId)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals(0L, body.totalPageViews)
        assertEquals(0.0, body.averageReadTimeSeconds)
        assertEquals(0L, body.totalLikes)
        assertEquals(0L, body.totalShares)
        assertEquals(0L, body.totalComments)
    }
}
