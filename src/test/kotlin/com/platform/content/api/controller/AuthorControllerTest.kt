package com.platform.content.api.controller

import com.platform.content.api.dto.AuthorResponse
import com.platform.content.application.author.AuthorService
import com.platform.content.domain.ConflictException
import com.platform.content.domain.EntityNotFoundException
import com.platform.content.domain.model.Author
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.UUID

class AuthorControllerTest {

    private lateinit var authorService: AuthorService
    private lateinit var controller: AuthorController

    @BeforeEach
    fun setUp() {
        authorService = mockk()
        controller = AuthorController(authorService)
    }

    @Test
    fun `create should return 201 with author response`() {
        val request = com.platform.content.api.dto.CreateAuthorRequest(
            name = "Jane Doe",
            email = "jane@example.com",
            bio = "A writer"
        )
        val author = Author(
            id = UUID.randomUUID(),
            name = "Jane Doe",
            email = "jane@example.com",
            bio = "A writer",
            createdAt = Instant.parse("2024-01-15T10:30:00.000Z")
        )
        every { authorService.create("Jane Doe", "jane@example.com", "A writer") } returns author

        val response = controller.create(request)

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val body = response.body!!
        assertEquals(author.id, body.id)
        assertEquals("Jane Doe", body.name)
        assertEquals("jane@example.com", body.email)
        assertEquals("A writer", body.bio)
        assertEquals("2024-01-15T10:30:00.000Z", body.createdAt)
    }

    @Test
    fun `create should pass null bio when not provided`() {
        val request = com.platform.content.api.dto.CreateAuthorRequest(
            name = "John Smith",
            email = "john@example.com",
            bio = null
        )
        val author = Author(
            id = UUID.randomUUID(),
            name = "John Smith",
            email = "john@example.com",
            bio = null,
            createdAt = Instant.now()
        )
        every { authorService.create("John Smith", "john@example.com", null) } returns author

        val response = controller.create(request)

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertNull(response.body!!.bio)
        verify { authorService.create("John Smith", "john@example.com", null) }
    }

    @Test
    fun `getById should return 200 with author response`() {
        val authorId = UUID.randomUUID()
        val author = Author(
            id = authorId,
            name = "Jane Doe",
            email = "jane@example.com",
            bio = "A writer",
            createdAt = Instant.parse("2024-01-15T10:30:00.000Z")
        )
        every { authorService.findById(authorId) } returns author

        val response = controller.getById(authorId)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals(authorId, body.id)
        assertEquals("Jane Doe", body.name)
        assertEquals("jane@example.com", body.email)
        assertEquals("A writer", body.bio)
    }

    @Test
    fun `getById should propagate EntityNotFoundException`() {
        val authorId = UUID.randomUUID()
        every { authorService.findById(authorId) } throws EntityNotFoundException("Author", authorId)

        val ex = org.junit.jupiter.api.assertThrows<EntityNotFoundException> {
            controller.getById(authorId)
        }
        assertEquals("Author with id $authorId not found", ex.message)
    }

    @Test
    fun `update should return 200 with updated author response`() {
        val authorId = UUID.randomUUID()
        val request = com.platform.content.api.dto.UpdateAuthorRequest(
            name = "Jane Updated",
            email = "jane.updated@example.com",
            bio = "Updated bio"
        )
        val updatedAuthor = Author(
            id = authorId,
            name = "Jane Updated",
            email = "jane.updated@example.com",
            bio = "Updated bio",
            createdAt = Instant.parse("2024-01-15T10:30:00.000Z")
        )
        every {
            authorService.update(authorId, "Jane Updated", "jane.updated@example.com", "Updated bio")
        } returns updatedAuthor

        val response = controller.update(authorId, request)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals(authorId, body.id)
        assertEquals("Jane Updated", body.name)
        assertEquals("jane.updated@example.com", body.email)
        assertEquals("Updated bio", body.bio)
    }

    @Test
    fun `update should propagate EntityNotFoundException for non-existent author`() {
        val authorId = UUID.randomUUID()
        val request = com.platform.content.api.dto.UpdateAuthorRequest(
            name = "Jane",
            email = "jane@example.com",
            bio = null
        )
        every {
            authorService.update(authorId, "Jane", "jane@example.com", null)
        } throws EntityNotFoundException("Author", authorId)

        val ex = org.junit.jupiter.api.assertThrows<EntityNotFoundException> {
            controller.update(authorId, request)
        }
        assertEquals("Author with id $authorId not found", ex.message)
    }

    @Test
    fun `update should propagate ConflictException for duplicate email`() {
        val authorId = UUID.randomUUID()
        val request = com.platform.content.api.dto.UpdateAuthorRequest(
            name = "Jane",
            email = "existing@example.com",
            bio = null
        )
        every {
            authorService.update(authorId, "Jane", "existing@example.com", null)
        } throws ConflictException("Author", "Email address 'existing@example.com' is already in use")

        val ex = org.junit.jupiter.api.assertThrows<ConflictException> {
            controller.update(authorId, request)
        }
        assertEquals("Author conflict: Email address 'existing@example.com' is already in use", ex.message)
    }

    @Test
    fun `delete should return 204 No Content`() {
        val authorId = UUID.randomUUID()
        every { authorService.delete(authorId) } returns Unit

        val response = controller.delete(authorId)

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        assertNull(response.body)
        verify { authorService.delete(authorId) }
    }

    @Test
    fun `delete should propagate ConflictException when author has articles`() {
        val authorId = UUID.randomUUID()
        every {
            authorService.delete(authorId)
        } throws ConflictException("Author", "Author has associated articles and cannot be deleted")

        val ex = org.junit.jupiter.api.assertThrows<ConflictException> {
            controller.delete(authorId)
        }
        assertEquals("Author conflict: Author has associated articles and cannot be deleted", ex.message)
    }

    @Test
    fun `delete should propagate EntityNotFoundException for non-existent author`() {
        val authorId = UUID.randomUUID()
        every { authorService.delete(authorId) } throws EntityNotFoundException("Author", authorId)

        val ex = org.junit.jupiter.api.assertThrows<EntityNotFoundException> {
            controller.delete(authorId)
        }
        assertEquals("Author with id $authorId not found", ex.message)
    }
}
