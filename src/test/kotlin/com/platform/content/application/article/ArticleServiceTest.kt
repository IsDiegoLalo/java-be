package com.platform.content.application.article

import com.platform.content.domain.ConflictException
import com.platform.content.domain.EntityNotFoundException
import com.platform.content.domain.ValidationException
import com.platform.content.domain.model.Article
import com.platform.content.domain.model.ArticleStatus
import com.platform.content.domain.model.Author
import com.platform.content.domain.model.Category
import com.platform.content.domain.port.ArticleFilter
import com.platform.content.domain.port.ArticleRepository
import com.platform.content.domain.port.AuthorRepository
import com.platform.content.domain.port.CategoryRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.Instant
import java.util.UUID

class ArticleServiceTest {

    private val articleRepository: ArticleRepository = mockk()
    private val authorRepository: AuthorRepository = mockk()
    private val categoryRepository: CategoryRepository = mockk()
    private val articleValidator: ArticleValidator = ArticleValidator()

    private lateinit var service: ArticleService

    private val authorId = UUID.randomUUID()
    private val categoryId = UUID.randomUUID()
    private val articleId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        service = ArticleService(articleRepository, authorRepository, categoryRepository, articleValidator)

        every { authorRepository.findById(authorId) } returns Author(
            id = authorId,
            name = "John Doe",
            email = "john@example.com",
            bio = null,
            createdAt = Instant.now()
        )
        every { categoryRepository.findById(categoryId) } returns Category(
            id = categoryId,
            name = "Technology",
            description = null,
            slug = "technology"
        )

        val articleSlot = slot<Article>()
        every { articleRepository.save(capture(articleSlot)) } answers { articleSlot.captured }
    }

    // --- Create ---

    @Test
    fun `create should return article with draft status`() {
        val result = service.create("Title", "Body content", "Summary", authorId, categoryId, listOf("kotlin"))

        assertEquals(ArticleStatus.Draft, result.status)
        assertNull(result.publishedAt)
    }

    @Test
    fun `create should set createdAt and updatedAt timestamps`() {
        val before = Instant.now()
        val result = service.create("Title", "Body content", null, authorId, categoryId, emptyList())
        val after = Instant.now()

        assertNotNull(result.createdAt)
        assertNotNull(result.updatedAt)
        assertEquals(result.createdAt, result.updatedAt)
        assert(result.createdAt >= before && result.createdAt <= after)
    }

    @Test
    fun `create should generate a UUID for the article`() {
        val result = service.create("Title", "Body content", null, authorId, categoryId, emptyList())

        assertNotNull(result.id)
    }

    @Test
    fun `create should throw EntityNotFoundException when author does not exist`() {
        val nonExistentAuthorId = UUID.randomUUID()
        every { authorRepository.findById(nonExistentAuthorId) } returns null

        val ex = assertThrows<EntityNotFoundException> {
            service.create("Title", "Body", null, nonExistentAuthorId, categoryId, emptyList())
        }
        assertEquals("Author", ex.entityType)
        assertEquals(nonExistentAuthorId, ex.entityId)
    }

    @Test
    fun `create should throw EntityNotFoundException when category does not exist`() {
        val nonExistentCategoryId = UUID.randomUUID()
        every { categoryRepository.findById(nonExistentCategoryId) } returns null

        val ex = assertThrows<EntityNotFoundException> {
            service.create("Title", "Body", null, authorId, nonExistentCategoryId, emptyList())
        }
        assertEquals("Category", ex.entityType)
        assertEquals(nonExistentCategoryId, ex.entityId)
    }

    @Test
    fun `create should throw ValidationException when title is blank`() {
        assertThrows<ValidationException> {
            service.create("", "Body", null, authorId, categoryId, emptyList())
        }
    }

    @Test
    fun `create should throw ValidationException when body is blank`() {
        assertThrows<ValidationException> {
            service.create("Title", "  ", null, authorId, categoryId, emptyList())
        }
    }

    @Test
    fun `create should throw ValidationException when tags exceed 10`() {
        val tooManyTags = (1..11).map { "tag$it" }
        assertThrows<ValidationException> {
            service.create("Title", "Body", null, authorId, categoryId, tooManyTags)
        }
    }

    // --- FindById ---

    @Test
    fun `findById should return article when it exists`() {
        val article = createTestArticle(articleId)
        every { articleRepository.findById(articleId) } returns article

        val result = service.findById(articleId)

        assertEquals(article, result)
    }

    @Test
    fun `findById should throw EntityNotFoundException when article does not exist`() {
        val nonExistentId = UUID.randomUUID()
        every { articleRepository.findById(nonExistentId) } returns null

        val ex = assertThrows<EntityNotFoundException> {
            service.findById(nonExistentId)
        }
        assertEquals("Article", ex.entityType)
        assertEquals(nonExistentId, ex.entityId)
    }

    // --- Update ---

    @Test
    fun `update should update fields and set new updatedAt`() {
        val existing = createTestArticle(articleId)
        every { articleRepository.findById(articleId) } returns existing

        val result = service.update(articleId, "New Title", "New Body", "New Summary", authorId, categoryId, listOf("updated"))

        assertEquals("New Title", result.title)
        assertEquals("New Body", result.body)
        assertEquals("New Summary", result.summary)
        assertEquals(listOf("updated"), result.tags)
        assert(result.updatedAt >= existing.updatedAt)
    }

    @Test
    fun `update should throw EntityNotFoundException when article does not exist`() {
        val nonExistentId = UUID.randomUUID()
        every { articleRepository.findById(nonExistentId) } returns null

        val ex = assertThrows<EntityNotFoundException> {
            service.update(nonExistentId, "Title", "Body", null, authorId, categoryId, emptyList())
        }
        assertEquals("Article", ex.entityType)
    }

    @Test
    fun `update should throw EntityNotFoundException when author does not exist`() {
        val existing = createTestArticle(articleId)
        every { articleRepository.findById(articleId) } returns existing
        val nonExistentAuthorId = UUID.randomUUID()
        every { authorRepository.findById(nonExistentAuthorId) } returns null

        val ex = assertThrows<EntityNotFoundException> {
            service.update(articleId, "Title", "Body", null, nonExistentAuthorId, categoryId, emptyList())
        }
        assertEquals("Author", ex.entityType)
    }

    @Test
    fun `update should throw EntityNotFoundException when category does not exist`() {
        val existing = createTestArticle(articleId)
        every { articleRepository.findById(articleId) } returns existing
        val nonExistentCategoryId = UUID.randomUUID()
        every { categoryRepository.findById(nonExistentCategoryId) } returns null

        val ex = assertThrows<EntityNotFoundException> {
            service.update(articleId, "Title", "Body", null, authorId, nonExistentCategoryId, emptyList())
        }
        assertEquals("Category", ex.entityType)
    }

    @Test
    fun `update should throw ValidationException for invalid fields`() {
        val existing = createTestArticle(articleId)
        every { articleRepository.findById(articleId) } returns existing

        assertThrows<ValidationException> {
            service.update(articleId, "", "Body", null, authorId, categoryId, emptyList())
        }
    }

    // --- List ---

    @Test
    fun `list should delegate to repository with filter and pageable`() {
        val filter = ArticleFilter(authorId = authorId)
        val pageable = PageRequest.of(0, 20)
        val articles = listOf(createTestArticle(UUID.randomUUID()))
        val page = PageImpl(articles, pageable, 1L)

        every { articleRepository.findAll(filter, pageable) } returns page

        val result = service.list(filter, pageable)

        assertEquals(1, result.totalElements)
        assertEquals(articles, result.content)
        verify(exactly = 1) { articleRepository.findAll(filter, pageable) }
    }

    @Test
    fun `list should return empty page when no articles match`() {
        val filter = ArticleFilter(status = ArticleStatus.Published)
        val pageable = PageRequest.of(0, 20)
        val emptyPage = PageImpl<Article>(emptyList(), pageable, 0L)

        every { articleRepository.findAll(filter, pageable) } returns emptyPage

        val result = service.list(filter, pageable)

        assertEquals(0, result.totalElements)
        assert(result.content.isEmpty())
    }

    // --- Delete ---

    @Test
    fun `delete should succeed for draft article`() {
        val article = createTestArticle(articleId, status = ArticleStatus.Draft)
        every { articleRepository.findById(articleId) } returns article
        every { articleRepository.deleteById(articleId) } returns Unit

        service.delete(articleId)

        verify(exactly = 1) { articleRepository.deleteById(articleId) }
    }

    @Test
    fun `delete should succeed for review article`() {
        val article = createTestArticle(articleId, status = ArticleStatus.Review)
        every { articleRepository.findById(articleId) } returns article
        every { articleRepository.deleteById(articleId) } returns Unit

        service.delete(articleId)

        verify(exactly = 1) { articleRepository.deleteById(articleId) }
    }

    @Test
    fun `delete should throw ConflictException for published article`() {
        val article = createTestArticle(articleId, status = ArticleStatus.Published)
        every { articleRepository.findById(articleId) } returns article

        val ex = assertThrows<ConflictException> {
            service.delete(articleId)
        }
        assertEquals("Article", ex.entityType)
        assertEquals("published articles cannot be deleted", ex.conflictReason)
    }

    @Test
    fun `delete should throw EntityNotFoundException when article does not exist`() {
        val nonExistentId = UUID.randomUUID()
        every { articleRepository.findById(nonExistentId) } returns null

        val ex = assertThrows<EntityNotFoundException> {
            service.delete(nonExistentId)
        }
        assertEquals("Article", ex.entityType)
        assertEquals(nonExistentId, ex.entityId)
    }

    // --- Helpers ---

    private fun createTestArticle(
        id: UUID,
        status: ArticleStatus = ArticleStatus.Draft
    ): Article {
        return Article(
            id = id,
            title = "Test Article",
            body = "Test body content",
            summary = "Test summary",
            authorId = authorId,
            categoryId = categoryId,
            tags = listOf("kotlin", "spring"),
            status = status,
            createdAt = Instant.now().minusSeconds(3600),
            updatedAt = Instant.now().minusSeconds(1800),
            publishedAt = if (status is ArticleStatus.Published) Instant.now() else null
        )
    }
}
