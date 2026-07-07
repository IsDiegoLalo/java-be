package com.platform.content.infrastructure.persistence

import com.platform.content.domain.model.Category
import com.platform.content.domain.port.CategoryRepository
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Adapter implementing the domain CategoryRepository port using Spring Data JPA.
 * Converts between domain Category model and JPA CategoryEntity.
 */
@Repository
class JpaCategoryRepository(
    private val springDataRepository: SpringDataCategoryRepository
) : CategoryRepository {

    override fun save(category: Category): Category {
        val entity = CategoryEntity.fromDomain(category)
        return springDataRepository.save(entity).toDomain()
    }

    override fun findById(id: UUID): Category? {
        return springDataRepository.findById(id)
            .map { it.toDomain() }
            .orElse(null)
    }

    override fun findBySlug(slug: String): Category? {
        return springDataRepository.findBySlug(slug)?.toDomain()
    }

    override fun findByNameIgnoreCase(name: String): Category? {
        return springDataRepository.findByNameIgnoreCase(name)?.toDomain()
    }

    override fun deleteById(id: UUID) {
        springDataRepository.deleteById(id)
    }

    override fun existsByNameIgnoreCase(name: String): Boolean {
        return springDataRepository.existsByNameIgnoreCase(name)
    }

    override fun existsBySlug(slug: String): Boolean {
        return springDataRepository.existsBySlug(slug)
    }
}
