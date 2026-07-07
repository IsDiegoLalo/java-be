package com.platform.content.application.search

import com.platform.content.domain.ValidationException
import com.platform.content.domain.model.Article
import com.platform.content.domain.model.ArticleStatus
import com.platform.content.domain.port.ArticleSearchPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.Instant
import java.util.UUID

class SearchServiceTest {

    private val articleSearchPort: ArticleSearchPort = mockk()

    private lateinit var service: SearchService

    @BeforeEach
    fun setUp() {
        service = SearchService(articleSearchPort)
    }

    // --- Successful search delegation ---

    @Test
    fun `should delegate valid query to ArticleSearchPort`() {
        val pageable = PageRequest.of(0, 20)
        val articles = listOf(
            createArticle("Kotlin Guide"),
            createArticle("Kotlin Coroutines")
        )
        val expectedPage = PageImpl(articles, pageable, 2L)
        every { articleSearchPort.search("Kotlin", pageable) } returns expectedPage

        val result = service.search("Kotlin", pageable)

        assertEquals(2, result.totalElements)
        assertEquals(articles, result.content)
        verify(exactly = 1) { articleSearchPort.search("Kotlin", pageable) }
    }

    @Test
    fun `should return empty page when no results match`() {
        val pageable = PageRequest.of(0, 20)
        val emptyPage = PageImpl<Article>(emptyList(), pageable, 0L)
        every { articleSearchPort.search("nonexistent", pageable) } returns emptyPage

        val result = service.search("nonexistent", pageable)

        assertEquals(0, result.totalElements)
        assertEquals(emptyList<Article>(), result.content)
    }

    @Test
    fun `should accept query at exactly 200 characters`() {
        val pageable = PageRequest.of(0, 20)
        val query = "a".repeat(200)
        val emptyPage = PageImpl<Article>(emptyList(), pageable, 0L)
        every { articleSearchPort.search(query, pageable) } returns emptyPage

        val result = service.search(query, pageable)

        assertEquals(0, result.totalElements)
        verify(exactly = 1) { articleSearchPort.search(query, pageable) }
    }

    // --- Blank query validation ---

    @Test
    fun `should throw ValidationException for empty query`() {
        val pageable = PageRequest.of(0, 20)

        val ex = assertThrows<ValidationException> {
            service.search("", pageable)
        }

        assertEquals(1, ex.fieldErrors.size)
        assertEquals("query", ex.fieldErrors[0].field)
        assertEquals("Search term must not be empty", ex.fieldErrors[0].message)
    }

    @Test
    fun `should throw ValidationException for whitespace-only query`() {
        val pageable = PageRequest.of(0, 20)

        val ex = assertThrows<ValidationException> {
            service.search("   ", pageable)
        }

        assertEquals(1, ex.fieldErrors.size)
        assertEquals("query", ex.fieldErrors[0].field)
        assertEquals("Search term must not be empty", ex.fieldErrors[0].message)
    }

    @Test
    fun `should throw ValidationException for tab and newline whitespace query`() {
        val pageable = PageRequest.of(0, 20)

        val ex = assertThrows<ValidationException> {
            service.search("\t\n", pageable)
        }

        assertEquals(1, ex.fieldErrors.size)
        assertEquals("query", ex.fieldErrors[0].field)
        assertEquals("Search term must not be empty", ex.fieldErrors[0].message)
    }

    // --- Query length validation ---

    @Test
    fun `should throw ValidationException for query exceeding 200 characters`() {
        val pageable = PageRequest.of(0, 20)
        val longQuery = "a".repeat(201)

        val ex = assertThrows<ValidationException> {
            service.search(longQuery, pageable)
        }

        assertEquals(1, ex.fieldErrors.size)
        assertEquals("query", ex.fieldErrors[0].field)
        assertEquals("Search term must not exceed 200 characters", ex.fieldErrors[0].message)
    }

    // --- Verifies no port interaction on validation failure ---

    @Test
    fun `should not call ArticleSearchPort when query is blank`() {
        val pageable = PageRequest.of(0, 20)

        assertThrows<ValidationException> {
            service.search("", pageable)
        }

        verify(exactly = 0) { articleSearchPort.search(any(), any()) }
    }

    @Test
    fun `should not call ArticleSearchPort when query exceeds max length`() {
        val pageable = PageRequest.of(0, 20)

        assertThrows<ValidationException> {
            service.search("a".repeat(201), pageable)
        }

        verify(exactly = 0) { articleSearchPort.search(any(), any()) }
    }

    private fun createArticle(title: String): Article = Article(
        id = UUID.randomUUID(),
        title = title,
        body = "Body content for $title",
        summary = null,
        authorId = UUID.randomUUID(),
        categoryId = UUID.randomUUID(),
        tags = emptyList(),
        status = ArticleStatus.Published,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        publishedAt = Instant.now()
    )
}
