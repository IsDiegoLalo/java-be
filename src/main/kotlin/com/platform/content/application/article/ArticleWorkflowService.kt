package com.platform.content.application.article

import com.platform.content.domain.EntityNotFoundException
import com.platform.content.domain.InvalidTransitionException
import com.platform.content.domain.model.Article
import com.platform.content.domain.model.ArticlePublishedEvent
import com.platform.content.domain.model.ArticleStatus
import com.platform.content.domain.port.ArticleEventPublisher
import com.platform.content.domain.port.ArticleRepository
import com.platform.content.domain.port.CategoryRepository
import com.platform.content.domain.workflow.TransitionResult
import com.platform.content.domain.workflow.WorkflowTransitionValidator
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Application service responsible for managing article status transitions
 * through the editorial workflow.
 *
 * Coordinates between the workflow validator, article persistence,
 * and event publishing following the hexagonal architecture pattern (DIP).
 */
@Service
class ArticleWorkflowService(
    private val articleRepository: ArticleRepository,
    private val categoryRepository: CategoryRepository,
    private val workflowValidator: WorkflowTransitionValidator,
    private val eventPublisher: ArticleEventPublisher
) {

    /**
     * Transitions an article to the specified target status.
     *
     * Steps:
     * 1. Find the article (or throw EntityNotFoundException)
     * 2. Validate the transition using the workflow state machine
     * 3. Update timestamps (updatedAt always, publishedAt on publish)
     * 4. Save the updated article
     * 5. Emit ArticlePublishedEvent if transitioning to Published
     *
     * @param articleId the ID of the article to transition
     * @param targetStatus the desired target status
     * @return the updated article
     * @throws EntityNotFoundException if the article does not exist
     * @throws InvalidTransitionException if the transition violates workflow rules
     */
    fun transitionStatus(articleId: UUID, targetStatus: ArticleStatus): Article {
        val article = articleRepository.findById(articleId)
            ?: throw EntityNotFoundException("Article", articleId)

        val result = workflowValidator.validate(article.status, targetStatus)

        when (result) {
            is TransitionResult.Valid -> { /* transition allowed */ }
            is TransitionResult.Invalid -> throw InvalidTransitionException(
                currentState = result.currentState,
                targetState = targetStatus,
                allowedTransitions = result.allowed
            )
        }

        val now = Instant.now()
        val updatedArticle = article.copy(
            status = targetStatus,
            updatedAt = now,
            publishedAt = if (targetStatus is ArticleStatus.Published) now else article.publishedAt
        )

        val savedArticle = articleRepository.save(updatedArticle)

        if (targetStatus is ArticleStatus.Published) {
            val category = categoryRepository.findById(savedArticle.categoryId)
            val event = ArticlePublishedEvent(
                articleId = savedArticle.id,
                title = savedArticle.title,
                authorId = savedArticle.authorId,
                category = category?.name ?: "",
                tags = savedArticle.tags,
                publishedAt = savedArticle.publishedAt!!
            )
            eventPublisher.publishArticlePublished(event)
        }

        return savedArticle
    }
}
