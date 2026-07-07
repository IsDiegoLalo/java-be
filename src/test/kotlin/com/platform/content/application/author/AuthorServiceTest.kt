package com.platform.content.application.author

import com.platform.content.domain.ConflictException
import com.platform.content.domain.EntityNotFoundException
import com.platform.content.domain.ValidationException
import com.platform.content.domain.model.Author
import com.platform.content.domain.port.ArticleRepository
import com.platform.content.domain.port.AuthorRepository
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

class AuthorServiceTest {

    private val authorRepository: AuthorRepository = mockk()
    private val articleRepository: ArticleRepository = mockk()
    private val authorValidator: AuthorValidator = mockk()

    private val authorService = AuthorService(authorRepository, articleRepository, authorValidator)

    private val authorId = UUID.randomUUID()
    private val existingAuthor = Author(
        id = authorId,
        name = "John Doe",
        email = "john@example.com",
        bio = "A writer",
        createdAt = Instant.now()
    )

    // --- Create ---

    @Test
    fun `create should validate, check uniqueness, and save author`() {
        justRun { authorValidator.validate("Jane", "jane@example.com", "Bio") }
        every { authorRepository.findByEmail("jane@example.com") } returns null
        val authorSlot = slot<Author>()
        every { authorRepository.save(capture(authorSlot)) } answers { authorSlot.captured }

        val result = authorService.create("Jane", "jane@example.com", "Bio")

        assertEquals("Jane", result.name)
        assertEquals("jane@example.com", result.email)
        assertEquals("Bio", result.bio)
        assertNotNull(result.id)
        assertNotNull(result.createdAt)
        verify { authorValidator.validate("Jane", "jane@example.com", "Bio") }
    }

    @Test
    fun `create should throw ConflictException when email already exists`() {
        justRun { authorValidator.validate("Jane", "john@example.com", null) }
        every { authorRepository.findByEmail("john@example.com") } returns existingAuthor

        val ex = assertThrows<ConflictException> {
            authorService.create("Jane", "john@example.com", null)
        }

        assertEquals("Author", ex.entityType)
        assert(ex.conflictReason.contains("already in use"))
    }

    @Test
    fun `create should propagate ValidationException from validator`() {
        every { authorValidator.validate("", "bad", null) } throws ValidationException(
            listOf(com.platform.content.domain.FieldError("name", "Name must not be blank"))
        )

        assertThrows<ValidationException> {
            authorService.create("", "bad", null)
        }
    }

    // --- FindById ---

    @Test
    fun `findById should return author when found`() {
        every { authorRepository.findById(authorId) } returns existingAuthor

        val result = authorService.findById(authorId)

        assertEquals(existingAuthor, result)
    }

    @Test
    fun `findById should throw EntityNotFoundException when not found`() {
        val missingId = UUID.randomUUID()
        every { authorRepository.findById(missingId) } returns null

        val ex = assertThrows<EntityNotFoundException> {
            authorService.findById(missingId)
        }

        assertEquals("Author", ex.entityType)
        assertEquals(missingId, ex.entityId)
    }

    // --- Update ---

    @Test
    fun `update should validate, check uniqueness excluding self, and save`() {
        every { authorRepository.findById(authorId) } returns existingAuthor
        justRun { authorValidator.validate("Updated Name", "john@example.com", "New bio") }
        every { authorRepository.findByEmail("john@example.com") } returns existingAuthor // same author
        val authorSlot = slot<Author>()
        every { authorRepository.save(capture(authorSlot)) } answers { authorSlot.captured }

        val result = authorService.update(authorId, "Updated Name", "john@example.com", "New bio")

        assertEquals("Updated Name", result.name)
        assertEquals("john@example.com", result.email)
        assertEquals("New bio", result.bio)
        assertEquals(authorId, result.id)
    }

    @Test
    fun `update should throw EntityNotFoundException when author not found`() {
        val missingId = UUID.randomUUID()
        every { authorRepository.findById(missingId) } returns null

        val ex = assertThrows<EntityNotFoundException> {
            authorService.update(missingId, "Name", "email@test.com", null)
        }

        assertEquals("Author", ex.entityType)
        assertEquals(missingId, ex.entityId)
    }

    @Test
    fun `update should throw ConflictException when email belongs to another author`() {
        val otherAuthor = Author(
            id = UUID.randomUUID(),
            name = "Other",
            email = "taken@example.com",
            bio = null,
            createdAt = Instant.now()
        )
        every { authorRepository.findById(authorId) } returns existingAuthor
        justRun { authorValidator.validate("John Doe", "taken@example.com", null) }
        every { authorRepository.findByEmail("taken@example.com") } returns otherAuthor

        val ex = assertThrows<ConflictException> {
            authorService.update(authorId, "John Doe", "taken@example.com", null)
        }

        assertEquals("Author", ex.entityType)
        assert(ex.conflictReason.contains("already in use"))
    }

    // --- Delete ---

    @Test
    fun `delete should remove author when no articles exist`() {
        every { authorRepository.findById(authorId) } returns existingAuthor
        every { articleRepository.existsByAuthorId(authorId) } returns false
        justRun { authorRepository.deleteById(authorId) }

        authorService.delete(authorId)

        verify { authorRepository.deleteById(authorId) }
    }

    @Test
    fun `delete should throw EntityNotFoundException when author not found`() {
        val missingId = UUID.randomUUID()
        every { authorRepository.findById(missingId) } returns null

        val ex = assertThrows<EntityNotFoundException> {
            authorService.delete(missingId)
        }

        assertEquals("Author", ex.entityType)
        assertEquals(missingId, ex.entityId)
    }

    @Test
    fun `delete should throw ConflictException when author has articles`() {
        every { authorRepository.findById(authorId) } returns existingAuthor
        every { articleRepository.existsByAuthorId(authorId) } returns true

        val ex = assertThrows<ConflictException> {
            authorService.delete(authorId)
        }

        assertEquals("Author", ex.entityType)
        assert(ex.conflictReason.contains("associated articles"))
    }
}
