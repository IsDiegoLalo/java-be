package com.platform.content.api.controller

import com.platform.content.api.dto.CreateArticleRequest
import com.platform.content.api.dto.UpdateArticleRequest
import com.platform.content.application.article.ArticleService
import com.platform.content.domain.ConflictException
import com.platform.content.domain.EntityNotFoundException
import com.platform.content.domain.model.Article
import com.platform.content.domain.model.ArticleStatus
import com.platform.content.domain.port.ArticleFilter
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.UUID

class ArticleControllerTest {

    private lateinit var articleService: ArticleService
    private lateinit var controller: ArticleController

    private val authorId = UUID.randomUUID()
    private val categoryId = UUID.randomUUID()
    private val now = Instant.parse("2024-03-10T12:00:00.000Z")

    @BeforeEach
    fun setUp() {
        articleService = mockk()
        controller = ArticleController(articleService)
    }

    private fun sampleArticle(
        id: UUID = UUID.randomUUID(),
        status: ArticleStatus = ArticleStatus.Draft
    ) = Article(
        id = id,
        title = "Test Article",
        body = "Article body content",
        summary = "A summary",
        authorId = authorId,
        categoryId = categoryId,
        tags = listOf("kotlin", "spring"),
        status = status,
        createdAt = now,
        updatedAt = now,
        publishedAt = null
    )

    @Test
    fun `create should return 201 with article response`() {
        val request = CreateArticleRequest(
            title = "Test Article",
            body = "Article body content",
            summary = "A summary",
            authorId = authorId,
            categoryId = categoryId,
            tags = listOf("kotlin", "spring")
        )
        val article = sampleArticle()
        every {
            articleService.create("Test Article", "Article body content", "A summary", authorId, categoryId, listOf("kotlin", "spring"))
        } returns article

        val response = controller.create(request)

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val body = response.body!!
        assertEquals(article.id, body.id)
        assertEquals("Test Article", body.title)
        assertEquals("Article body content", body.body)
        assertEquals("A summary", body.summary)
        assertEquals(authorId, body.authorId)
        assertEquals(categoryId, body.categoryId)
        assertEquals(listOf("kotlin", "spring"), body.tags)
        assertEquals("draft", body.status)
    }

    @Test
    fun `create should handle null summary`() {
        val request = CreateArticleRequest(
            title = "Title",
            body = "Body",
            summary = null,
            authorId = authorId,
            categoryId = categoryId,
            tags = emptyList()
        )
        val article = sampleArticle().copy(title = "Title", body = "Body", summary = null, tags = emptyList())
        every {
            articleService.create("Title", "Body", null, authorId, categoryId, emptyList())
        } returns article

        val response = controller.create(request)

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertNull(response.body!!.summary)
    }

    @Test
    fun `create should propagate EntityNotFoundException for invalid author`() {
        val request = CreateArticleRequest(
            title = "Title",
            body = "Body",
            summary = null,
            authorId = authorId,
            categoryId = categoryId,
            tags = emptyList()
        )
        every {
            articleService.create("Title", "Body", null, authorId, categoryId, emptyList())
        } throws EntityNotFoundException("Author", authorId)

        assertThrows<EntityNotFoundException> {
            controller.create(request)
        }
    }

    @Test
    fun `getById should return 200 with article response`() {
        val articleId = UUID.randomUUID()
        val article = sampleArticle(id = articleId)
        every { articleService.findById(articleId) } returns article

        val response = controller.getById(articleId)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals(articleId, body.id)
        assertEquals("Test Article", body.title)
    }

    @Test
    fun `getById should propagate EntityNotFoundException`() {
        val articleId = UUID.randomUUID()
        every { articleService.findById(articleId) } throws EntityNotFoundException("Article", articleId)

        assertThrows<EntityNotFoundException> {
            controller.getById(articleId)
        }
    }

    @Test
    fun `update should return 200 with updated article response`() {
        val articleId = UUID.randomUUID()
        val request = UpdateArticleRequest(
            title = "Updated Title",
            body = "Updated body",
            summary = "Updated summary",
            authorId = authorId,
            categoryId = categoryId,
            tags = listOf("updated")
        )
        val updatedArticle = sampleArticle(id = articleId).copy(
            title = "Updated Title",
            body = "Updated body",
            summary = "Updated summary",
            tags = listOf("updated")
        )
        every {
            articleService.update(articleId, "Updated Title", "Updated body", "Updated summary", authorId, categoryId, listOf("updated"))
        } returns updatedArticle

        val response = controller.update(articleId, request)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals(articleId, body.id)
        assertEquals("Updated Title", body.title)
        assertEquals("Updated body", body.body)
        assertEquals("Updated summary", body.summary)
        assertEquals(listOf("updated"), body.tags)
    }

    @Test
    fun `update should propagate EntityNotFoundException`() {
        val articleId = UUID.randomUUID()
        val request = UpdateArticleRequest(
            title = "Title",
            body = "Body",
            summary = null,
            authorId = authorId,
            categoryId = categoryId,
            tags = emptyList()
        )
        every {
            articleService.update(articleId, "Title", "Body", null, authorId, categoryId, emptyList())
        } throws EntityNotFoundException("Article", articleId)

        assertThrows<EntityNotFoundException> {
            controller.update(articleId, request)
        }
    }

    @Test
    fun `delete should return 204 No Content`() {
        val articleId = UUID.randomUUID()
        every { articleService.delete(articleId) } returns Unit

        val response = controller.delete(articleId)

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        assertNull(response.body)
        verify { articleService.delete(articleId) }
    }

    @Test
    fun `delete should propagate ConflictException for published articles`() {
        val articleId = UUID.randomUUID()
        every {
            articleService.delete(articleId)
        } throws ConflictException("Article", "published articles cannot be deleted")

        assertThrows<ConflictException> {
            controller.delete(articleId)
        }
    }

    @Test
    fun `delete should propagate EntityNotFoundException`() {
        val articleId = UUID.randomUUID()
        every { articleService.delete(articleId) } throws EntityNotFoundException("Article", articleId)

        assertThrows<EntityNotFoundException> {
            controller.delete(articleId)
        }
    }

    @Test
    fun `list should return 200 with paginated articles`() {
        val article1 = sampleArticle()
        val article2 = sampleArticle()
        val pageable = PageRequest.of(0, 20)
        val page = PageImpl(listOf(article1, article2), pageable, 2)
        val filter = ArticleFilter()

        every { articleService.list(filter, pageable) } returns page

        val response = controller.list(
            authorId = null,
            categoryId = null,
            status = null,
            tags = null,
            page = 0,
            size = 20
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals(2, body.content.size)
        assertEquals(0, body.page)
        assertEquals(20, body.size)
        assertEquals(2L, body.totalElements)
        assertEquals(1, body.totalPages)
    }

    @Test
    fun `list should apply filters when provided`() {
        val pageable = PageRequest.of(0, 10)
        val filter = ArticleFilter(
            authorId = authorId,
            categoryId = categoryId,
            status = ArticleStatus.Draft,
            tags = listOf("kotlin")
        )
        val page = PageImpl(emptyList<Article>(), pageable, 0)

        every { articleService.list(filter, pageable) } returns page

        val response = controller.list(
            authorId = authorId,
            categoryId = categoryId,
            status = "draft",
            tags = listOf("kotlin"),
            page = 0,
            size = 10
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(0, response.body!!.content.size)
        verify { articleService.list(filter, pageable) }
    }

    @Test
    fun `list should use default pagination when not specified`() {
        val pageable = PageRequest.of(0, 20)
        val filter = ArticleFilter()
        val page = PageImpl(emptyList<Article>(), pageable, 0)

        every { articleService.list(filter, pageable) } returns page

        val response = controller.list(
            authorId = null,
            categoryId = null,
            status = null,
            tags = null,
            page = 0,
            size = 20
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        verify { articleService.list(filter, pageable) }
    }
}
