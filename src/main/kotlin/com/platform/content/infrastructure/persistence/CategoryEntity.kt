package com.platform.content.infrastructure.persistence

import com.platform.content.domain.model.Category
import jakarta.persistence.*
import java.util.UUID

/**
 * JPA entity mapping for the categories table.
 * Uses database-level UUID generation (gen_random_uuid()).
 */
@Entity
@Table(name = "categories")
class CategoryEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "name", nullable = false, length = 100)
    var name: String = "",

    @Column(name = "description", length = 500)
    var description: String? = null,

    @Column(name = "slug", nullable = false, unique = true, length = 120)
    var slug: String = ""
) {
    /**
     * Converts this JPA entity to the domain model.
     */
    fun toDomain(): Category = Category(
        id = id,
        name = name,
        description = description,
        slug = slug
    )

    companion object {
        /**
         * Creates a JPA entity from the domain model.
         */
        fun fromDomain(category: Category): CategoryEntity = CategoryEntity(
            id = category.id,
            name = category.name,
            description = category.description,
            slug = category.slug
        )
    }
}
