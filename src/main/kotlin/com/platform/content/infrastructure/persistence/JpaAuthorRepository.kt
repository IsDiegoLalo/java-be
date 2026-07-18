package com.platform.content.infrastructure.persistence

import com.platform.content.domain.model.Author
import com.platform.content.domain.port.AuthorRepository
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Adapter implementing the domain AuthorRepository port using Spring Data JPA.
 * Converts between domain Author model and JPA AuthorEntity.
 */
@Repository
class JpaAuthorRepository(
    private val springDataRepository: SpringDataAuthorRepository
) : AuthorRepository {

    override fun save(author: Author): Author {
        val entity = AuthorEntity.fromDomain(author)
        return springDataRepository.save(entity).toDomain()
    }

    override fun findById(id: UUID): Author? {
        return springDataRepository.findById(id)
            .map { it.toDomain() }
            .orElse(null)
    }

    override fun findByEmail(email: String): Author? {
        return springDataRepository.findByEmail(email)?.toDomain()
    }

    override fun deleteById(id: UUID) {
        springDataRepository.deleteById(id)
    }

    override fun existsByEmail(email: String): Boolean {
        return springDataRepository.existsByEmail(email)
    }

    override fun findAll(): List<Author> {
    return springDataRepository.findAll().map { it.toDomain() }
}

}
