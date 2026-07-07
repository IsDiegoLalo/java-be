package com.platform.content.api.controller

import com.platform.content.application.search.SearchService
import com.platform.content.domain.FieldError
import com.platform.content.domain.ValidationException
import com.platform.content.domain.model.Article
import com.platform.content.domain.model.ArticleStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.UUID

class SearchControllerTest {

    private lateinit var searchService: SearchService
    private lateinit var controller: SearchController

    @BeforeEach
    fun setUp() {
        searchService = mockk()
        controller = SearchController(searchService)
    }

    @Test
    fun `search should return 200 with paginated results`() {
        val query = "kotlin"
        val pageable = PageRequest.of(0, 20)
        val article = Article(
            id = UUID.randomUUID(),
            title = "Kotlin Guide",
            body = "A comprehensive guide to Kotlin programming",
            summary = "Learn Kotlin",
            authorId = UUID.randomUUID(),
            categoryId = UUID.randomUUID(),
            tags = listOf("kotlin", "programming"),
            status = ArticleStatus.Published,
            createdAt = Instant.parse("2024-01-15T10:30:00.000Z"),
            updatedAt = Instant.parse("2024-01-16T08:00:00.000Z"),
            publishedAt = Instant.parse("2024-01-16T08:00:00.000Z")
        )
        val page = PageImpl(listOf(article), pageable, 1L)
        every { searchService.search(query, pageable) } returns page

        val response = controller.search(q = query, page = 0, size = 20)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals(1, body.content.size)
        assertEquals("Kotlin Guide", body.content[0].title)
        assertEquals(0, body.page)
        assertEquals(20, body.size)
        assertEquals(1L, body.totalElements)
        assertEquals(1, body.totalPages)
    }

    @Test
    fun `search should pass custom pagination parameters to service`() {
        val query = "spring"
        val pageable = PageRequest.of(2, 10)
        val page = PageImpl(emptyList<Article>(), pageable, 0L)
        every { searchService.search(query, pageable) } returns page

        val response = controller.search(q = query, page = 2, size = 10)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals(0, body.content.size)
        assertEquals(2, body.page)
        assertEquals(10, body.size)
        assertEquals(0L, body.totalElements)
        assertEquals(0, body.totalPages)
        verify { searchService.search(query, pageable) }
    }

    @Test
    fun `search should propagate ValidationException for blank query`() {
        val query = "   "
        val pageable = PageRequest.of(0, 20)
        every { searchService.search(query, pageable) } throws ValidationException(
            listOf(FieldError("query", "Search term must not be empty"))
        )

        val ex = org.junit.jupiter.api.assertThrows<ValidationException> {
            controller.search(q = query, page = 0, size = 20)
        }
        assertEquals(1, ex.fieldErrors.size)
        assertEquals("query", ex.fieldErrors[0].field)
        assertEquals("Search term must not be empty", ex.fieldErrors[0].message)
    }

    @Test
    fun `search should propagate ValidationException for query exceeding 200 chars`() {
        val query = "a".repeat(201)
        val pageable = PageRequest.of(0, 20)
        every { searchService.search(query, pageable) } throws ValidationException(
            listOf(FieldError("query", "Search term must not exceed 200 characters"))
        )

        val ex = org.junit.jupiter.api.assertThrows<ValidationException> {
            controller.search(q = query, page = 0, size = 20)
        }
        assertEquals(1, ex.fieldErrors.size)
        assertEquals("query", ex.fieldErrors[0].field)
        assertEquals("Search term must not exceed 200 characters", ex.fieldErrors[0].message)
    }

    @Test
    fun `search should return multiple results with correct mapping`() {
        val query = "programming"
        val pageable = PageRequest.of(0, 20)
        val article1 = Article(
            id = UUID.randomUUID(),
            title = "Java Programming",
            body = "Java is a widely-used programming language",
            summary = null,
            authorId = UUID.randomUUID(),
            categoryId = UUID.randomUUID(),
            tags = listOf("java"),
            status = ArticleStatus.Published,
            createdAt = Instant.parse("2024-01-10T09:00:00.000Z"),
            updatedAt = Instant.parse("2024-01-12T14:00:00.000Z"),
            publishedAt = Instant.parse("2024-01-12T14:00:00.000Z")
        )
        val article2 = Article(
            id = UUID.randomUUID(),
            title = "Kotlin Programming",
            body = "Kotlin is a modern programming language",
            summary = "Modern language",
            authorId = UUID.randomUUID(),
            categoryId = UUID.randomUUID(),
            tags = emptyList(),
            status = ArticleStatus.Published,
            createdAt = Instant.parse("2024-02-01T11:00:00.000Z"),
            updatedAt = Instant.parse("2024-02-03T16:00:00.000Z"),
            publishedAt = Instant.parse("2024-02-03T16:00:00.000Z")
        )
        val page = PageImpl(listOf(article1, article2), pageable, 2L)
        every { searchService.search(query, pageable) } returns page

        val response = controller.search(q = query, page = 0, size = 20)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals(2, body.content.size)
        assertEquals("Java Programming", body.content[0].title)
        assertEquals("published", body.content[0].status)
        assertEquals("Kotlin Programming", body.content[1].title)
        assertEquals("published", body.content[1].status)
        assertEquals(2L, body.totalElements)
        assertEquals(1, body.totalPages)
    }

    @Test
    fun `search should use default pagination when not specified`() {
        val query = "test"
        val defaultPageable = PageRequest.of(0, 20)
        val page = PageImpl(emptyList<Article>(), defaultPageable, 0L)
        every { searchService.search(query, defaultPageable) } returns page

        val response = controller.search(q = query, page = 0, size = 20)

        assertEquals(HttpStatus.OK, response.statusCode)
        verify { searchService.search(query, defaultPageable) }
    }
}
