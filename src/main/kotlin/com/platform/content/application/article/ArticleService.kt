package com.platform.content.application.article

import com.platform.content.domain.ConflictException
import com.platform.content.domain.EntityNotFoundException
import com.platform.content.domain.model.Article
import com.platform.content.domain.model.ArticleStatus
import com.platform.content.domain.port.ArticleFilter
import com.platform.content.domain.port.ArticleRepository
import com.platform.content.domain.port.AuthorRepository
import com.platform.content.domain.port.CategoryRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Application service orchestrating article CRUD use cases (SRP).
 *
 * Validates article fields via [ArticleValidator], checks foreign key references
 * (author, category) exist, and enforces business rules such as draft-on-create
 * and published-article deletion guard.
 *
 * Depends on abstractions (DIP): [ArticleRepository], [AuthorRepository],
 * [CategoryRepository], and [ArticleValidator].
 */
@Service
class ArticleService(
    private val articleRepository: ArticleRepository,
    private val authorRepository: AuthorRepository,
    private val categoryRepository: CategoryRepository,
    private val articleValidator: ArticleValidator
) {

    /**
     * Creates a new article with status [ArticleStatus.Draft].
     *
     * Validates fields, checks that the referenced author and category exist,
     * generates a new UUID, and sets timestamps.
     *
     * @throws com.platform.content.domain.ValidationException if field validation fails
     * @throws EntityNotFoundException if author or category does not exist
     */
    fun create(
        title: String,
        body: String,
        summary: String?,
        authorId: UUID,
        categoryId: UUID,
        tags: List<String>
    ): Article {
        articleValidator.validate(title, body, summary, tags)
        checkAuthorExists(authorId)
        checkCategoryExists(categoryId)

        val now = Instant.now()
        val article = Article(
            id = UUID.randomUUID(),
            title = title,
            body = body,
            summary = summary,
            authorId = authorId,
            categoryId = categoryId,
            tags = tags,
            status = ArticleStatus.Draft,
            createdAt = now,
            updatedAt = now,
            publishedAt = null
        )

        return articleRepository.save(article)
    }

    /**
     * Retrieves an article by its ID.
     *
     * @throws EntityNotFoundException if the article does not exist
     */
    fun findById(id: UUID): Article {
        return articleRepository.findById(id)
            ?: throw EntityNotFoundException("Article", id)
    }

    /**
     * Updates an existing article's fields.
     *
     * Validates fields and checks FK references before persisting.
     * Updates the [Article.updatedAt] timestamp.
     *
     * @throws EntityNotFoundException if article, author, or category does not exist
     * @throws com.platform.content.domain.ValidationException if field validation fails
     */
    fun update(
        id: UUID,
        title: String,
        body: String,
        summary: String?,
        authorId: UUID,
        categoryId: UUID,
        tags: List<String>
    ): Article {
        val existing = articleRepository.findById(id)
            ?: throw EntityNotFoundException("Article", id)

        articleValidator.validate(title, body, summary, tags)
        checkAuthorExists(authorId)
        checkCategoryExists(categoryId)

        val updated = existing.copy(
            title = title,
            body = body,
            summary = summary,
            authorId = authorId,
            categoryId = categoryId,
            tags = tags,
            updatedAt = Instant.now()
        )

        return articleRepository.save(updated)
    }

    /**
     * Lists articles with filtering and pagination.
     *
     * Delegates to [ArticleRepository.findAll] with the provided filter and pageable.
     */
    fun list(filter: ArticleFilter, pageable: Pageable): Page<Article> {
        return articleRepository.findAll(filter, pageable)
    }

    /**
     * Deletes an article by its ID.
     *
     * Guards against deletion of published articles (requirement 3.10).
     *
     * @throws EntityNotFoundException if the article does not exist
     * @throws ConflictException if the article is in published status
     */
    fun delete(id: UUID) {
        val article = articleRepository.findById(id)
            ?: throw EntityNotFoundException("Article", id)

        if (article.status is ArticleStatus.Published) {
            throw ConflictException("Article", "published articles cannot be deleted")
        }

        articleRepository.deleteById(id)
    }

    private fun checkAuthorExists(authorId: UUID) {
        if (authorRepository.findById(authorId) == null) {
            throw EntityNotFoundException("Author", authorId)
        }
    }

    private fun checkCategoryExists(categoryId: UUID) {
        if (categoryRepository.findById(categoryId) == null) {
            throw EntityNotFoundException("Category", categoryId)
        }
    }
}
