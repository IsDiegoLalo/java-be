package com.platform.content.application.article

import com.platform.content.domain.EntityNotFoundException
import com.platform.content.domain.InvalidTransitionException
import com.platform.content.domain.model.Article
import com.platform.content.domain.model.ArticlePublishedEvent
import com.platform.content.domain.model.ArticleStatus
import com.platform.content.domain.model.Category
import com.platform.content.domain.port.ArticleEventPublisher
import com.platform.content.domain.port.ArticleRepository
import com.platform.content.domain.port.CategoryRepository
import com.platform.content.domain.workflow.TransitionResult
import com.platform.content.domain.workflow.WorkflowTransitionValidator
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

class ArticleWorkflowServiceTest {

    private val articleRepository: ArticleRepository = mockk()
    private val categoryRepository: CategoryRepository = mockk()
    private val workflowValidator: WorkflowTransitionValidator = mockk()
    private val eventPublisher: ArticleEventPublisher = mockk(relaxed = true)

    private lateinit var service: ArticleWorkflowService

    private val authorId = UUID.randomUUID()
    private val categoryId = UUID.randomUUID()
    private val articleId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        service = ArticleWorkflowService(articleRepository, categoryRepository, workflowValidator, eventPublisher)

        val articleSlot = slot<Article>()
        every { articleRepository.save(capture(articleSlot)) } answers { articleSlot.captured }
    }

    // --- Article not found ---

    @Test
    fun `transitionStatus should throw EntityNotFoundException when article does not exist`() {
        every { articleRepository.findById(articleId) } returns null

        val ex = assertThrows<EntityNotFoundException> {
            service.transitionStatus(articleId, ArticleStatus.Review)
        }
        assertEquals("Article", ex.entityType)
        assertEquals(articleId, ex.entityId)
    }

    // --- Invalid transition ---

    @Test
    fun `transitionStatus should throw InvalidTransitionException when transition is invalid`() {
        val article = createTestArticle(status = ArticleStatus.Draft)
        every { articleRepository.findById(articleId) } returns article
        every { workflowValidator.validate(ArticleStatus.Draft, ArticleStatus.Published) } returns
            TransitionResult.Invalid(
                currentState = ArticleStatus.Draft,
                allowed = setOf(ArticleStatus.Review)
            )

        val ex = assertThrows<InvalidTransitionException> {
            service.transitionStatus(articleId, ArticleStatus.Published)
        }
        assertEquals(ArticleStatus.Draft, ex.currentState)
        assertEquals(ArticleStatus.Published, ex.targetState)
        assertEquals(setOf(ArticleStatus.Review), ex.allowedTransitions)
    }

    @Test
    fun `transitionStatus should throw InvalidTransitionException from published state`() {
        val article = createTestArticle(status = ArticleStatus.Published)
        every { articleRepository.findById(articleId) } returns article
        every { workflowValidator.validate(ArticleStatus.Published, ArticleStatus.Draft) } returns
            TransitionResult.Invalid(
                currentState = ArticleStatus.Published,
                allowed = emptySet()
            )

        val ex = assertThrows<InvalidTransitionException> {
            service.transitionStatus(articleId, ArticleStatus.Draft)
        }
        assertEquals(ArticleStatus.Published, ex.currentState)
        assertEquals(ArticleStatus.Draft, ex.targetState)
        assertEquals(emptySet<ArticleStatus>(), ex.allowedTransitions)
    }

    // --- Valid transition: Draft → Review ---

    @Test
    fun `transitionStatus should transition from draft to review`() {
        val article = createTestArticle(status = ArticleStatus.Draft)
        every { articleRepository.findById(articleId) } returns article
        every { workflowValidator.validate(ArticleStatus.Draft, ArticleStatus.Review) } returns TransitionResult.Valid

        val result = service.transitionStatus(articleId, ArticleStatus.Review)

        assertEquals(ArticleStatus.Review, result.status)
    }

    @Test
    fun `transitionStatus should update updatedAt on transition to review`() {
        val originalUpdatedAt = Instant.now().minusSeconds(3600)
        val article = createTestArticle(status = ArticleStatus.Draft, updatedAt = originalUpdatedAt)
        every { articleRepository.findById(articleId) } returns article
        every { workflowValidator.validate(ArticleStatus.Draft, ArticleStatus.Review) } returns TransitionResult.Valid

        val before = Instant.now()
        val result = service.transitionStatus(articleId, ArticleStatus.Review)
        val after = Instant.now()

        assert(result.updatedAt >= before && result.updatedAt <= after)
    }

    @Test
    fun `transitionStatus should not set publishedAt on transition to review`() {
        val article = createTestArticle(status = ArticleStatus.Draft)
        every { articleRepository.findById(articleId) } returns article
        every { workflowValidator.validate(ArticleStatus.Draft, ArticleStatus.Review) } returns TransitionResult.Valid

        val result = service.transitionStatus(articleId, ArticleStatus.Review)

        assertEquals(null, result.publishedAt)
    }

    @Test
    fun `transitionStatus should not emit event on transition to review`() {
        val article = createTestArticle(status = ArticleStatus.Draft)
        every { articleRepository.findById(articleId) } returns article
        every { workflowValidator.validate(ArticleStatus.Draft, ArticleStatus.Review) } returns TransitionResult.Valid

        service.transitionStatus(articleId, ArticleStatus.Review)

        verify(exactly = 0) { eventPublisher.publishArticlePublished(any()) }
    }

    // --- Valid transition: Review → Published ---

    @Test
    fun `transitionStatus should transition from review to published`() {
        val article = createTestArticle(status = ArticleStatus.Review)
        every { articleRepository.findById(articleId) } returns article
        every { workflowValidator.validate(ArticleStatus.Review, ArticleStatus.Published) } returns TransitionResult.Valid
        every { categoryRepository.findById(categoryId) } returns Category(
            id = categoryId, name = "Technology", description = null, slug = "technology"
        )

        val result = service.transitionStatus(articleId, ArticleStatus.Published)

        assertEquals(ArticleStatus.Published, result.status)
    }

    @Test
    fun `transitionStatus should set publishedAt on transition to published`() {
        val article = createTestArticle(status = ArticleStatus.Review)
        every { articleRepository.findById(articleId) } returns article
        every { workflowValidator.validate(ArticleStatus.Review, ArticleStatus.Published) } returns TransitionResult.Valid
        every { categoryRepository.findById(categoryId) } returns Category(
            id = categoryId, name = "Technology", description = null, slug = "technology"
        )

        val before = Instant.now()
        val result = service.transitionStatus(articleId, ArticleStatus.Published)
        val after = Instant.now()

        assertNotNull(result.publishedAt)
        assert(result.publishedAt!! >= before && result.publishedAt!! <= after)
    }

    @Test
    fun `transitionStatus should update updatedAt on transition to published`() {
        val originalUpdatedAt = Instant.now().minusSeconds(3600)
        val article = createTestArticle(status = ArticleStatus.Review, updatedAt = originalUpdatedAt)
        every { articleRepository.findById(articleId) } returns article
        every { workflowValidator.validate(ArticleStatus.Review, ArticleStatus.Published) } returns TransitionResult.Valid
        every { categoryRepository.findById(categoryId) } returns Category(
            id = categoryId, name = "Technology", description = null, slug = "technology"
        )

        val before = Instant.now()
        val result = service.transitionStatus(articleId, ArticleStatus.Published)
        val after = Instant.now()

        assert(result.updatedAt >= before && result.updatedAt <= after)
    }

    @Test
    fun `transitionStatus should emit ArticlePublishedEvent on transition to published`() {
        val article = createTestArticle(status = ArticleStatus.Review)
        every { articleRepository.findById(articleId) } returns article
        every { workflowValidator.validate(ArticleStatus.Review, ArticleStatus.Published) } returns TransitionResult.Valid
        every { categoryRepository.findById(categoryId) } returns Category(
            id = categoryId, name = "Technology", description = null, slug = "technology"
        )

        service.transitionStatus(articleId, ArticleStatus.Published)

        val eventSlot = slot<ArticlePublishedEvent>()
        verify(exactly = 1) { eventPublisher.publishArticlePublished(capture(eventSlot)) }
        val event = eventSlot.captured
        assertEquals(articleId, event.articleId)
        assertEquals("Test Article", event.title)
        assertEquals(authorId, event.authorId)
        assertEquals("Technology", event.category)
        assertEquals(listOf("kotlin", "spring"), event.tags)
        assertNotNull(event.publishedAt)
    }

    // --- Valid transition: Review → Draft ---

    @Test
    fun `transitionStatus should transition from review back to draft`() {
        val article = createTestArticle(status = ArticleStatus.Review)
        every { articleRepository.findById(articleId) } returns article
        every { workflowValidator.validate(ArticleStatus.Review, ArticleStatus.Draft) } returns TransitionResult.Valid

        val result = service.transitionStatus(articleId, ArticleStatus.Draft)

        assertEquals(ArticleStatus.Draft, result.status)
    }

    @Test
    fun `transitionStatus should not emit event on transition back to draft`() {
        val article = createTestArticle(status = ArticleStatus.Review)
        every { articleRepository.findById(articleId) } returns article
        every { workflowValidator.validate(ArticleStatus.Review, ArticleStatus.Draft) } returns TransitionResult.Valid

        service.transitionStatus(articleId, ArticleStatus.Draft)

        verify(exactly = 0) { eventPublisher.publishArticlePublished(any()) }
    }

    // --- Save verification ---

    @Test
    fun `transitionStatus should save the updated article`() {
        val article = createTestArticle(status = ArticleStatus.Draft)
        every { articleRepository.findById(articleId) } returns article
        every { workflowValidator.validate(ArticleStatus.Draft, ArticleStatus.Review) } returns TransitionResult.Valid

        service.transitionStatus(articleId, ArticleStatus.Review)

        val savedSlot = slot<Article>()
        verify(exactly = 1) { articleRepository.save(capture(savedSlot)) }
        assertEquals(ArticleStatus.Review, savedSlot.captured.status)
    }

    // --- Helpers ---

    private fun createTestArticle(
        status: ArticleStatus = ArticleStatus.Draft,
        updatedAt: Instant = Instant.now().minusSeconds(1800)
    ): Article {
        return Article(
            id = articleId,
            title = "Test Article",
            body = "Test body content",
            summary = "Test summary",
            authorId = authorId,
            categoryId = categoryId,
            tags = listOf("kotlin", "spring"),
            status = status,
            createdAt = Instant.now().minusSeconds(3600),
            updatedAt = updatedAt,
            publishedAt = null
        )
    }
}
