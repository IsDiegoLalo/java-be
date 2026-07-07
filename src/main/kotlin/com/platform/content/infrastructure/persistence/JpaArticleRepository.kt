package com.platform.content.infrastructure.persistence

import com.platform.content.domain.model.Article
import com.platform.content.domain.port.ArticleFilter
import com.platform.content.domain.port.ArticleRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Adapter implementing the domain ArticleRepository port using Spring Data JPA.
 * Converts between domain Article model and JPA ArticleEntity.
 * Uses JPA Specifications for dynamic filtering and Spring's Pageable for pagination.
 */
@Repository
class JpaArticleRepository(
    private val springDataRepository: SpringDataArticleRepository
) : ArticleRepository {

    override fun save(article: Article): Article {
        val entity = ArticleEntity.fromDomain(article)
        return springDataRepository.save(entity).toDomain()
    }

    override fun findById(id: UUID): Article? {
        return springDataRepository.findById(id)
            .map { it.toDomain() }
            .orElse(null)
    }

    override fun findAll(filter: ArticleFilter, pageable: Pageable): Page<Article> {
        val specification = ArticleSpecifications.fromFilter(filter)
        return springDataRepository.findAll(specification, pageable)
            .map { it.toDomain() }
    }

    override fun deleteById(id: UUID) {
        springDataRepository.deleteById(id)
    }

    override fun existsByAuthorId(authorId: UUID): Boolean {
        return springDataRepository.existsByAuthorId(authorId)
    }

    override fun existsByCategoryId(categoryId: UUID): Boolean {
        return springDataRepository.existsByCategoryId(categoryId)
    }
}
