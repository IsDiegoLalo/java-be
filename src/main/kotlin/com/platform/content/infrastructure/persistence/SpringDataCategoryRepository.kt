package com.platform.content.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Spring Data JPA interface for CategoryEntity persistence.
 * Used internally by JpaCategoryRepository adapter.
 */
interface SpringDataCategoryRepository : JpaRepository<CategoryEntity, UUID> {
    fun findBySlug(slug: String): CategoryEntity?
    fun findByNameIgnoreCase(name: String): CategoryEntity?
    fun existsByNameIgnoreCase(name: String): Boolean
    fun existsBySlug(slug: String): Boolean
}
