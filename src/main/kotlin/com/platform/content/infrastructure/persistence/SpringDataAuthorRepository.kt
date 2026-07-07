package com.platform.content.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Spring Data JPA interface for AuthorEntity persistence.
 * Used internally by JpaAuthorRepository adapter.
 */
interface SpringDataAuthorRepository : JpaRepository<AuthorEntity, UUID> {
    fun findByEmail(email: String): AuthorEntity?
    fun existsByEmail(email: String): Boolean
}
