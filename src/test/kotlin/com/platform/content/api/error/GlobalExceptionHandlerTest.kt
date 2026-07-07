package com.platform.content.api.error

import com.platform.content.domain.ConflictException
import com.platform.content.domain.EntityNotFoundException
import com.platform.content.domain.FieldError
import com.platform.content.domain.InvalidTransitionException
import com.platform.content.domain.ValidationException
import com.platform.content.domain.model.ArticleStatus
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.validation.BindingResult
import org.springframework.validation.FieldError as SpringFieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import java.util.UUID

class GlobalExceptionHandlerTest {

    private lateinit var handler: GlobalExceptionHandler
    private lateinit var request: HttpServletRequest

    @BeforeEach
    fun setUp() {
        handler = GlobalExceptionHandler()
        request = mockk()
        every { request.requestURI } returns "/api/articles/123"
    }

    @Test
    fun `handleNotFound should return 404 with problem details`() {
        val entityId = UUID.randomUUID()
        val ex = EntityNotFoundException("Article", entityId)

        val response = handler.handleNotFound(ex, request)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        val body = response.body!!
        assertEquals("/problems/not-found", body.type)
        assertEquals("Not Found", body.title)
        assertEquals(404, body.status)
        assertEquals("Article with id $entityId not found", body.detail)
        assertEquals("/api/articles/123", body.instance)
        assertNull(body.fieldErrors)
    }

    @Test
    fun `handleConflict should return 409 with problem details`() {
        val ex = ConflictException("Author", "email already in use")

        val response = handler.handleConflict(ex, request)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        val body = response.body!!
        assertEquals("/problems/conflict", body.type)
        assertEquals("Conflict", body.title)
        assertEquals(409, body.status)
        assertEquals("Author conflict: email already in use", body.detail)
        assertEquals("/api/articles/123", body.instance)
        assertNull(body.fieldErrors)
    }

    @Test
    fun `handleInvalidTransition should return 422 with problem details`() {
        val ex = InvalidTransitionException(
            currentState = ArticleStatus.Published,
            targetState = ArticleStatus.Draft,
            allowedTransitions = emptySet()
        )

        val response = handler.handleInvalidTransition(ex, request)

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        val body = response.body!!
        assertEquals("/problems/invalid-transition", body.type)
        assertEquals("Invalid State Transition", body.title)
        assertEquals(422, body.status)
        assertEquals(
            "Cannot transition from published to draft. Allowed: []",
            body.detail
        )
        assertEquals("/api/articles/123", body.instance)
        assertNull(body.fieldErrors)
    }

    @Test
    fun `handleValidation should return 422 with field errors from binding result`() {
        val bindingResult = mockk<BindingResult>()
        val springFieldErrors = listOf(
            SpringFieldError("createArticleRequest", "title", "must not be blank"),
            SpringFieldError("createArticleRequest", "body", "must not be blank")
        )
        every { bindingResult.fieldErrors } returns springFieldErrors

        val ex = MethodArgumentNotValidException(
            mockk(relaxed = true),
            bindingResult
        )

        val response = handler.handleValidation(ex, request)

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        val body = response.body!!
        assertEquals("/problems/validation-error", body.type)
        assertEquals("Validation Error", body.title)
        assertEquals(422, body.status)
        assertEquals("One or more fields failed validation", body.detail)
        assertEquals("/api/articles/123", body.instance)
        assertNotNull(body.fieldErrors)
        assertEquals(2, body.fieldErrors!!.size)
        assertEquals("title", body.fieldErrors!![0].field)
        assertEquals("must not be blank", body.fieldErrors!![0].message)
        assertEquals("body", body.fieldErrors!![1].field)
        assertEquals("must not be blank", body.fieldErrors!![1].message)
    }

    @Test
    fun `handleDomainValidation should return 422 with domain field errors`() {
        val ex = ValidationException(
            fieldErrors = listOf(
                FieldError("name", "must be between 1 and 100 characters"),
                FieldError("email", "invalid email format")
            )
        )

        val response = handler.handleDomainValidation(ex, request)

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        val body = response.body!!
        assertEquals("/problems/validation-error", body.type)
        assertEquals("Validation Error", body.title)
        assertEquals(422, body.status)
        assertEquals("/api/articles/123", body.instance)
        assertNotNull(body.fieldErrors)
        assertEquals(2, body.fieldErrors!!.size)
        assertEquals("name", body.fieldErrors!![0].field)
        assertEquals("must be between 1 and 100 characters", body.fieldErrors!![0].message)
        assertEquals("email", body.fieldErrors!![1].field)
        assertEquals("invalid email format", body.fieldErrors!![1].message)
    }

    @Test
    fun `handleUnexpected should return 500 with generic message`() {
        val ex = RuntimeException("Something went terribly wrong with the database")

        val response = handler.handleUnexpected(ex, request)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        val body = response.body!!
        assertEquals("/problems/internal-error", body.type)
        assertEquals("Internal Server Error", body.title)
        assertEquals(500, body.status)
        assertEquals("An unexpected error occurred", body.detail)
        assertEquals("/api/articles/123", body.instance)
        assertNull(body.fieldErrors)
    }

    @Test
    fun `handleUnexpected should not leak internal error details`() {
        val ex = RuntimeException("SQL syntax error near SELECT * FROM users WHERE password='admin'")

        val response = handler.handleUnexpected(ex, request)

        val body = response.body!!
        assertEquals("An unexpected error occurred", body.detail)
        // The detail should never contain the actual exception message
        assert(!body.detail.contains("SQL"))
        assert(!body.detail.contains("password"))
    }

    @Test
    fun `handleNotFound should use request URI as instance`() {
        every { request.requestURI } returns "/api/authors/abc-123"
        val ex = EntityNotFoundException("Author", UUID.randomUUID())

        val response = handler.handleNotFound(ex, request)

        assertEquals("/api/authors/abc-123", response.body!!.instance)
    }

    @Test
    fun `handleInvalidTransition should include allowed transitions in detail`() {
        val ex = InvalidTransitionException(
            currentState = ArticleStatus.Draft,
            targetState = ArticleStatus.Published,
            allowedTransitions = setOf(ArticleStatus.Review)
        )

        val response = handler.handleInvalidTransition(ex, request)

        val body = response.body!!
        assertEquals(
            "Cannot transition from draft to published. Allowed: [review]",
            body.detail
        )
    }
}
