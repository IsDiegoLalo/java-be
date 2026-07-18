package com.platform.content.domain.port

import com.platform.content.domain.model.Author
import java.util.UUID

/**
 * Port for author persistence operations.
 * Implementations reside in the infrastructure layer (e.g., JPA adapter).
 */
interface AuthorRepository {
    fun save(author: Author): Author
    fun findAll(): List<Author>
    fun findById(id: UUID): Author?
    fun findByEmail(email: String): Author?
    fun deleteById(id: UUID)
    fun existsByEmail(email: String): Boolean
}
