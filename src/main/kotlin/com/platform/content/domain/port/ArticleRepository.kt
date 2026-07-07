package com.platform.content.domain.port

import com.platform.content.domain.model.Article
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.util.UUID

/**
 * Port for article persistence operations.
 * Implementations reside in the infrastructure layer (e.g., JPA adapter).
 */
interface ArticleRepository {
    fun save(article: Article): Article
    fun findById(id: UUID): Article?
    fun findAll(filter: ArticleFilter, pageable: Pageable): Page<Article>
    fun deleteById(id: UUID)
    fun existsByAuthorId(authorId: UUID): Boolean
    fun existsByCategoryId(categoryId: UUID): Boolean
}
