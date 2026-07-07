package com.platform.content.infrastructure.persistence

import com.platform.content.domain.model.Author
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * JPA entity mapping for the authors table.
 * Uses database-level UUID generation (gen_random_uuid()).
 */
@Entity
@Table(name = "authors")
class AuthorEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "name", nullable = false, length = 100)
    var name: String = "",

    @Column(name = "email", nullable = false, unique = true, length = 255)
    var email: String = "",

    @Column(name = "bio", length = 500)
    var bio: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
) {
    /**
     * Converts this JPA entity to the domain model.
     */
    fun toDomain(): Author = Author(
        id = id,
        name = name,
        email = email,
        bio = bio,
        createdAt = createdAt
    )

    companion object {
        /**
         * Creates a JPA entity from the domain model.
         */
        fun fromDomain(author: Author): AuthorEntity = AuthorEntity(
            id = author.id,
            name = author.name,
            email = author.email,
            bio = author.bio,
            createdAt = author.createdAt
        )
    }
}
