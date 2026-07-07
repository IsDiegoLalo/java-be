package com.platform.content.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.util.UUID

/**
 * Spring Data JPA interface for ArticleEntity persistence.
 * Extends JpaSpecificationExecutor to support dynamic filtering via Specifications.
 * Used internally by JpaArticleRepository adapter.
 */
interface SpringDataArticleRepository : JpaRepository<ArticleEntity, UUID>,
    JpaSpecificationExecutor<ArticleEntity> {

    fun existsByAuthorId(authorId: UUID): Boolean
    fun existsByCategoryId(categoryId: UUID): Boolean
}
