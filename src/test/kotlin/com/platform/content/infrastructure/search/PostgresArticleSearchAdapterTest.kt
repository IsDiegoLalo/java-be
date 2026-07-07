package com.platform.content.infrastructure.search

import com.platform.content.domain.model.ArticleStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class PostgresArticleSearchAdapterTest {

    private lateinit var entityManager: EntityManager
    private lateinit var adapter: PostgresArticleSearchAdapter

    @BeforeEach
    fun setUp() {
        entityManager = mockk()
        adapter = PostgresArticleSearchAdapter(entityManager)
    }

    @Test
    fun `search should return empty page when no results match`() {
        val countQuery = mockk<Query>()
        every { entityManager.createNativeQuery(match { it.contains("COUNT(*)") }) } returns countQuery
        every { countQuery.setParameter("query", any<String>()) } returns countQuery
        every { countQuery.singleResult } returns 0L

        val result = adapter.search("nonexistent", PageRequest.of(0, 20))

        assertTrue(result.content.isEmpty())
        assertEquals(0L, result.totalElements)
        assertEquals(0, result.totalPages)
    }

    @Test
    fun `search should return paginated results ranked by relevance`() {
        val countQuery = mockk<Query>()
        val searchQuery = mockk<Query>()

        val articleId = UUID.randomUUID()
        val authorId = UUID.randomUUID()
        val categoryId = UUID.randomUUID()
        val now = Instant.now()
        val timestamp = Timestamp.from(now)

        every { entityManager.createNativeQuery(match { it.contains("COUNT(*)") }) } returns countQuery
        every { countQuery.setParameter("query", any<String>()) } returns countQuery
        every { countQuery.singleResult } returns 1L

        every { entityManager.createNativeQuery(match { it.contains("ts_rank") }) } returns searchQuery
        every { searchQuery.setParameter("query", any<String>()) } returns searchQuery
        every { searchQuery.setParameter("limit", any<Int>()) } returns searchQuery
        every { searchQuery.setParameter("offset", any<Int>()) } returns searchQuery
        every { searchQuery.resultList } returns listOf(
            arrayOf<Any?>(
                articleId,          // id
                "Test Title",       // title
                "Test body text",   // body
                "A summary",        // summary
                authorId,           // author_id
                categoryId,         // category_id
                "{kotlin,spring}",  // tags (as PostgreSQL text[] string)
                "published",        // status
                timestamp,          // created_at
                timestamp,          // updated_at
                timestamp,          // published_at
                0.75f               // relevance score
            )
        )

        val result = adapter.search("test", PageRequest.of(0, 20))

        assertEquals(1, result.content.size)
        assertEquals(1L, result.totalElements)

        val article = result.content[0]
        assertEquals(articleId, article.id)
        assertEquals("Test Title", article.title)
        assertEquals("Test body text", article.body)
        assertEquals("A summary", article.summary)
        assertEquals(authorId, article.authorId)
        assertEquals(categoryId, article.categoryId)
        assertEquals(listOf("kotlin", "spring"), article.tags)
        assertEquals(ArticleStatus.Published, article.status)
        assertNotNull(article.createdAt)
        assertNotNull(article.updatedAt)
        assertNotNull(article.publishedAt)
    }

    @Test
    fun `search should handle null summary and published_at`() {
        val countQuery = mockk<Query>()
        val searchQuery = mockk<Query>()

        val articleId = UUID.randomUUID()
        val authorId = UUID.randomUUID()
        val categoryId = UUID.randomUUID()
        val timestamp = Timestamp.from(Instant.now())

        every { entityManager.createNativeQuery(match { it.contains("COUNT(*)") }) } returns countQuery
        every { countQuery.setParameter("query", any<String>()) } returns countQuery
        every { countQuery.singleResult } returns 1L

        every { entityManager.createNativeQuery(match { it.contains("ts_rank") }) } returns searchQuery
        every { searchQuery.setParameter("query", any<String>()) } returns searchQuery
        every { searchQuery.setParameter("limit", any<Int>()) } returns searchQuery
        every { searchQuery.setParameter("offset", any<Int>()) } returns searchQuery
        every { searchQuery.resultList } returns listOf(
            arrayOf<Any?>(
                articleId, "Title", "Body", null, authorId, categoryId,
                "{}", "published", timestamp, timestamp, null, 0.5f
            )
        )

        val result = adapter.search("kotlin", PageRequest.of(0, 10))

        val article = result.content[0]
        assertNull(article.summary)
        assertNull(article.publishedAt)
        assertEquals(emptyList<String>(), article.tags)
    }

    @Test
    fun `search should support pagination offset`() {
        val countQuery = mockk<Query>()
        val searchQuery = mockk<Query>()

        every { entityManager.createNativeQuery(match { it.contains("COUNT(*)") }) } returns countQuery
        every { countQuery.setParameter("query", any<String>()) } returns countQuery
        every { countQuery.singleResult } returns 25L

        every { entityManager.createNativeQuery(match { it.contains("ts_rank") }) } returns searchQuery
        every { searchQuery.setParameter("query", any<String>()) } returns searchQuery
        every { searchQuery.setParameter("limit", 10) } returns searchQuery
        every { searchQuery.setParameter("offset", 10) } returns searchQuery
        every { searchQuery.resultList } returns emptyList<Array<Any?>>()

        val result = adapter.search("test", PageRequest.of(1, 10))

        assertEquals(25L, result.totalElements)
        assertEquals(3, result.totalPages)
        verify { searchQuery.setParameter("offset", 10) }
        verify { searchQuery.setParameter("limit", 10) }
    }

    @Test
    fun `search should sanitize query with special characters`() {
        val countQuery = mockk<Query>()

        every { entityManager.createNativeQuery(match { it.contains("COUNT(*)") }) } returns countQuery
        every { countQuery.setParameter("query", "hello & world") } returns countQuery
        every { countQuery.singleResult } returns 0L

        adapter.search("hello! world?", PageRequest.of(0, 20))

        verify { countQuery.setParameter("query", "hello & world") }
    }

    @Test
    fun `search should join multi-word queries with AND operator`() {
        val countQuery = mockk<Query>()

        every { entityManager.createNativeQuery(match { it.contains("COUNT(*)") }) } returns countQuery
        every { countQuery.setParameter("query", "spring & boot & kotlin") } returns countQuery
        every { countQuery.singleResult } returns 0L

        adapter.search("spring boot kotlin", PageRequest.of(0, 20))

        verify { countQuery.setParameter("query", "spring & boot & kotlin") }
    }
}
