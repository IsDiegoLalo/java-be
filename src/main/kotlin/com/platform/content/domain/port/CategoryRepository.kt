package com.platform.content.domain.port

import com.platform.content.domain.model.Category
import java.util.UUID

/**
 * Port for category persistence operations.
 * Implementations reside in the infrastructure layer (e.g., JPA adapter).
 */
interface CategoryRepository {
    fun save(category: Category): Category
    fun findById(id: UUID): Category?
    fun findBySlug(slug: String): Category?
    fun findByNameIgnoreCase(name: String): Category?
    fun deleteById(id: UUID)
    fun existsByNameIgnoreCase(name: String): Boolean
    fun existsBySlug(slug: String): Boolean
}
