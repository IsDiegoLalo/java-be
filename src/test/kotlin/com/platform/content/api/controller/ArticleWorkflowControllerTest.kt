package com.platform.content.api.controller

import com.platform.content.api.dto.TransitionStatusRequest
import com.platform.content.application.article.ArticleWorkflowService
import com.platform.content.domain.EntityNotFoundException
import com.platform.content.domain.InvalidTransitionException
import com.platform.content.domain.model.Article
import com.platform.content.domain.model.ArticleStatus
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.UUID

class ArticleWorkflowControllerTest {

    private lateinit var articleWorkflowService: ArticleWorkflowService
    private lateinit var controller: ArticleWorkflowController

    private val authorId = UUID.randomUUID()
    private val categoryId = UUID.randomUUID()
    private val now = Instant.parse("2024-03-10T12:00:00.000Z")

    @BeforeEach
    fun setUp() {
        articleWorkflowService = mockk()
        controller = ArticleWorkflowController(articleWorkflowService)
    }

    private fun sampleArticle(
        id: UUID = UUID.randomUUID(),
        status: ArticleStatus = ArticleStatus.Draft,
        publishedAt: Instant? = null
    ) = Article(
        id = id,
        title = "Test Article",
        body = "Article body",
        summary = "Summary",
        authorId = authorId,
        categoryId = categoryId,
        tags = listOf("kotlin"),
        status = status,
        createdAt = now,
        updatedAt = now,
        publishedAt = publishedAt
    )

    @Test
    fun `transitionStatus should return 200 with updated article`() {
        val articleId = UUID.randomUUID()
        val request = TransitionStatusRequest(targetStatus = "review")
        val updatedArticle = sampleArticle(id = articleId, status = ArticleStatus.Review)

        every {
            articleWorkflowService.transitionStatus(articleId, ArticleStatus.Review)
        } returns updatedArticle

        val response = controller.transitionStatus(articleId, request)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals(articleId, body.id)
        assertEquals("review", body.status)
    }

    @Test
    fun `transitionStatus to published should include publishedAt`() {
        val articleId = UUID.randomUUID()
        val publishTime = Instant.parse("2024-03-10T14:00:00.000Z")
        val request = TransitionStatusRequest(targetStatus = "published")
        val publishedArticle = sampleArticle(
            id = articleId,
            status = ArticleStatus.Published,
            publishedAt = publishTime
        )

        every {
            articleWorkflowService.transitionStatus(articleId, ArticleStatus.Published)
        } returns publishedArticle

        val response = controller.transitionStatus(articleId, request)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals("published", body.status)
        assertEquals("2024-03-10T14:00:00.000Z", body.publishedAt)
    }

    @Test
    fun `transitionStatus should propagate EntityNotFoundException`() {
        val articleId = UUID.randomUUID()
        val request = TransitionStatusRequest(targetStatus = "review")

        every {
            articleWorkflowService.transitionStatus(articleId, ArticleStatus.Review)
        } throws EntityNotFoundException("Article", articleId)

        assertThrows<EntityNotFoundException> {
            controller.transitionStatus(articleId, request)
        }
    }

    @Test
    fun `transitionStatus should propagate InvalidTransitionException`() {
        val articleId = UUID.randomUUID()
        val request = TransitionStatusRequest(targetStatus = "published")

        every {
            articleWorkflowService.transitionStatus(articleId, ArticleStatus.Published)
        } throws InvalidTransitionException(
            currentState = ArticleStatus.Draft,
            targetState = ArticleStatus.Published,
            allowedTransitions = setOf(ArticleStatus.Review)
        )

        assertThrows<InvalidTransitionException> {
            controller.transitionStatus(articleId, request)
        }
    }

    @Test
    fun `transitionStatus should propagate IllegalArgumentException for invalid status`() {
        val articleId = UUID.randomUUID()
        val request = TransitionStatusRequest(targetStatus = "invalid_status")

        assertThrows<IllegalArgumentException> {
            controller.transitionStatus(articleId, request)
        }
    }
}
