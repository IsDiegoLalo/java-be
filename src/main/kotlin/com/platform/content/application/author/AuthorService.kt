package com.platform.content.application.author

import com.platform.content.domain.ConflictException
import com.platform.content.domain.EntityNotFoundException
import com.platform.content.domain.model.Author
import com.platform.content.domain.port.ArticleRepository
import com.platform.content.domain.port.AuthorRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Application service for Author management use cases.
 * Orchestrates validation, uniqueness checks, and persistence via domain ports.
 */
@Service
@Transactional
class AuthorService(
    private val authorRepository: AuthorRepository,
    private val articleRepository: ArticleRepository,
    private val authorValidator: AuthorValidator
) {

    /**
     * Creates a new author after validating fields and checking email uniqueness.
     *
     * @param name the author's name (1–100 characters, non-blank)
     * @param email the author's email (RFC 5322 format, max 255 characters)
     * @param bio optional bio (max 500 characters)
     * @return the persisted Author entity
     * @throws com.platform.content.domain.ValidationException if field validation fails
     * @throws ConflictException if the email is already in use
     */
    fun create(name: String, email: String, bio: String?): Author {
        authorValidator.validate(name, email, bio)
        checkEmailUniqueness(email)

        val author = Author(
            id = UUID.randomUUID(),
            name = name,
            email = email,
            bio = bio,
            createdAt = Instant.now()
        )

        return authorRepository.save(author)
    }

    /**
     * Retrieves an author by ID.
     *
     * @param id the author's unique identifier
     * @return the Author entity
     * @throws EntityNotFoundException if no author exists with the given ID
     */
    @Transactional(readOnly = true)
    fun findById(id: UUID): Author {
        return authorRepository.findById(id)
            ?: throw EntityNotFoundException("Author", id)
    }

    /**
     * Updates an existing author after re-validating fields and checking email uniqueness.
     *
     * @param id the author's unique identifier
     * @param name the updated name
     * @param email the updated email
     * @param bio the updated bio (nullable)
     * @return the updated Author entity
     * @throws EntityNotFoundException if no author exists with the given ID
     * @throws com.platform.content.domain.ValidationException if field validation fails
     * @throws ConflictException if the email is already in use by another author
     */
    fun update(id: UUID, name: String, email: String, bio: String?): Author {
        val existingAuthor = authorRepository.findById(id)
            ?: throw EntityNotFoundException("Author", id)

        authorValidator.validate(name, email, bio)
        checkEmailUniqueness(email, excludeId = id)

        val updatedAuthor = existingAuthor.copy(
            name = name,
            email = email,
            bio = bio
        )

        return authorRepository.save(updatedAuthor)
    }

    /**
     * Deletes an author if they have no associated articles.
     *
     * @param id the author's unique identifier
     * @throws EntityNotFoundException if no author exists with the given ID
     * @throws ConflictException if the author has one or more associated articles
     */
    fun delete(id: UUID) {
        authorRepository.findById(id)
            ?: throw EntityNotFoundException("Author", id)

        if (articleRepository.existsByAuthorId(id)) {
            throw ConflictException("Author", "Author has associated articles and cannot be deleted")
        }

        authorRepository.deleteById(id)
    }

    /**
     * Checks that no other author (optionally excluding one by ID) already uses the given email.
     */
    private fun checkEmailUniqueness(email: String, excludeId: UUID? = null) {
        val existingAuthor = authorRepository.findByEmail(email)
        if (existingAuthor != null && existingAuthor.id != excludeId) {
            throw ConflictException("Author", "Email address '$email' is already in use")
        }
    }
}
