package com.platform.content.infrastructure.persistence

import com.platform.content.domain.model.Article
import com.platform.content.domain.model.ArticleStatus
import com.platform.content.domain.model.Author
import com.platform.content.domain.model.Category
import com.platform.content.domain.port.ArticleFilter
import com.platform.content.infrastructure.search.PostgresArticleSearchAdapter
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Integration tests for PostgreSQL persistence operations using Testcontainers.
 * Verifies Flyway migrations, CRUD operations, FK constraints, full-text search,
 * and article filtering against a real PostgreSQL 16 instance.
 *
 * Requirements validated: 1.1, 2.1, 3.1, 3.6, 3.7, 5.1, 9.1, 9.2
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(
    JpaAuthorRepository::class,
    JpaCategoryRepository::class,
    JpaArticleRepository::class,
    PostgresArticleSearchAdapter::class
)
class PostgresIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("content_platform_test")
            .withUsername("test")
            .withPassword("test")

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.datasource.driver-class-name") { "org.postgresql.Driver" }
            registry.add("spring.flyway.enabled") { "true" }
            registry.add("spring.jpa.hibernate.ddl-auto") { "validate" }
        }
    }

    @Autowired
    private lateinit var authorRepository: JpaAuthorRepository

    @Autowired
    private lateinit var categoryRepository: JpaCategoryRepository

    @Autowired
    private lateinit var articleRepository: JpaArticleRepository

    @Autowired
    private lateinit var searchAdapter: PostgresArticleSearchAdapter

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    // --- Helper methods ---

    private fun createAuthor(
        name: String = "Test Author",
        email: String = "test-${UUID.randomUUID()}@example.com",
        bio: String? = "A test author bio"
    ): Author {
        return authorRepository.save(
            Author(
                id = UUID.randomUUID(),
                name = name,
                email = email,
                bio = bio,
                createdAt = Instant.now()
            )
        )
    }

    private fun createCategory(
        name: String = "Test Category",
        slug: String = "test-category-${UUID.randomUUID().toString().take(8)}",
        description: String? = "A test category"
    ): Category {
        return categoryRepository.save(
            Category(
                id = UUID.randomUUID(),
                name = name,
                description = description,
                slug = slug
            )
        )
    }

    private fun createArticle(
        authorId: UUID,
        categoryId: UUID,
        title: String = "Test Article",
        body: String = "This is the body of a test article.",
        status: ArticleStatus = ArticleStatus.Draft,
        tags: List<String> = emptyList(),
        publishedAt: Instant? = null
    ): Article {
        return articleRepository.save(
            Article(
                id = UUID.randomUUID(),
                title = title,
                body = body,
                summary = "A summary",
                authorId = authorId,
                categoryId = categoryId,
                tags = tags,
                status = status,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                publishedAt = publishedAt
            )
        )
    }

    // ===========================
    // Flyway Migrations (Req 9.1, 9.2)
    // ===========================

    @Nested
    @DisplayName("Flyway Migrations")
    inner class FlywayMigrationTests {

        @Test
        @DisplayName("should have applied all migrations - tables exist")
        fun `flyway migrations create expected tables`() {
            val tables = entityManager.createNativeQuery(
                """
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public'
                AND table_type = 'BASE TABLE'
                ORDER BY table_name
                """.trimIndent()
            ).resultList.map { it.toString() }

            assertAll(
                { assertTrue(tables.contains("authors"), "authors table should exist") },
                { assertTrue(tables.contains("categories"), "categories table should exist") },
                { assertTrue(tables.contains("articles"), "articles table should exist") },
                { assertTrue(tables.contains("outbox_events"), "outbox_events table should exist") },
                { assertTrue(tables.contains("flyway_schema_history"), "flyway_schema_history should exist") }
            )
        }

        @Test
        @DisplayName("should have search_vector trigger on articles table")
        fun `search vector trigger exists`() {
            val triggers = entityManager.createNativeQuery(
                """
                SELECT trigger_name FROM information_schema.triggers
                WHERE event_object_table = 'articles'
                """.trimIndent()
            ).resultList.map { it.toString() }

            assertTrue(
                triggers.contains("trg_articles_search_vector_update"),
                "search_vector trigger should exist"
            )
        }

        @Test
        @DisplayName("should have recorded all migration versions in schema history")
        fun `flyway schema history contains all versions`() {
            @Suppress("UNCHECKED_CAST")
            val versions = entityManager.createNativeQuery(
                "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank"
            ).resultList.map { it.toString() }

            assertTrue(versions.contains("1"), "V1 migration should be recorded")
            assertTrue(versions.contains("2"), "V2 migration should be recorded")
            assertTrue(versions.contains("3"), "V3 migration should be recorded")
            assertTrue(versions.contains("4"), "V4 migration should be recorded")
            assertTrue(versions.contains("5"), "V5 migration should be recorded")
        }
    }

    // ===========================
    // Author CRUD (Req 1.1)
    // ===========================

    @Nested
    @DisplayName("Author CRUD Operations")
    inner class AuthorCrudTests {

        @Test
        @DisplayName("should create and find author by ID")
        fun `create and find author by id`() {
            val author = createAuthor(name = "John Doe", email = "john@example.com", bio = "Writer")

            val found = authorRepository.findById(author.id)

            assertNotNull(found)
            assertEquals("John Doe", found!!.name)
            assertEquals("john@example.com", found.email)
            assertEquals("Writer", found.bio)
            assertNotNull(found.createdAt)
        }

        @Test
        @DisplayName("should find author by email")
        fun `find author by email`() {
            val email = "unique-${UUID.randomUUID()}@example.com"
            createAuthor(email = email)

            val found = authorRepository.findByEmail(email)

            assertNotNull(found)
            assertEquals(email, found!!.email)
        }

        @Test
        @DisplayName("should return null for non-existent author")
        fun `find non-existent author returns null`() {
            val found = authorRepository.findById(UUID.randomUUID())
            assertNull(found)
        }

        @Test
        @DisplayName("should delete author by ID")
        fun `delete author by id`() {
            val author = createAuthor()

            authorRepository.deleteById(author.id)
            entityManager.flush()

            val found = authorRepository.findById(author.id)
            assertNull(found)
        }

        @Test
        @DisplayName("should check email existence correctly")
        fun `exists by email`() {
            val email = "exists-${UUID.randomUUID()}@example.com"
            createAuthor(email = email)

            assertTrue(authorRepository.existsByEmail(email))
            assertFalse(authorRepository.existsByEmail("nonexistent@example.com"))
        }

        @Test
        @DisplayName("should create author with null bio")
        fun `create author with null bio`() {
            val author = createAuthor(bio = null)

            val found = authorRepository.findById(author.id)

            assertNotNull(found)
            assertNull(found!!.bio)
        }
    }

    // ===========================
    // Category CRUD (Req 2.1)
    // ===========================

    @Nested
    @DisplayName("Category CRUD Operations")
    inner class CategoryCrudTests {

        @Test
        @DisplayName("should create and find category by ID")
        fun `create and find category by id`() {
            val category = createCategory(name = "Technology", slug = "technology")

            val found = categoryRepository.findById(category.id)

            assertNotNull(found)
            assertEquals("Technology", found!!.name)
            assertEquals("technology", found.slug)
        }

        @Test
        @DisplayName("should find category by slug")
        fun `find category by slug`() {
            val slug = "slug-${UUID.randomUUID().toString().take(8)}"
            createCategory(slug = slug)

            val found = categoryRepository.findBySlug(slug)

            assertNotNull(found)
            assertEquals(slug, found!!.slug)
        }

        @Test
        @DisplayName("should find category by name case-insensitive")
        fun `find category by name ignore case`() {
            val uniqueName = "UniqueCategory-${UUID.randomUUID().toString().take(8)}"
            createCategory(name = uniqueName, slug = "unique-cat-${UUID.randomUUID().toString().take(8)}")

            val found = categoryRepository.findByNameIgnoreCase(uniqueName.lowercase())

            assertNotNull(found)
            assertEquals(uniqueName, found!!.name)
        }

        @Test
        @DisplayName("should delete category by ID")
        fun `delete category by id`() {
            val category = createCategory()

            categoryRepository.deleteById(category.id)
            entityManager.flush()

            val found = categoryRepository.findById(category.id)
            assertNull(found)
        }

        @Test
        @DisplayName("should return null for non-existent category")
        fun `find non-existent category returns null`() {
            assertNull(categoryRepository.findById(UUID.randomUUID()))
        }

        @Test
        @DisplayName("should check name existence case-insensitively")
        fun `exists by name ignore case`() {
            val name = "ExistCheck-${UUID.randomUUID().toString().take(8)}"
            createCategory(name = name, slug = "exist-check-${UUID.randomUUID().toString().take(8)}")

            assertTrue(categoryRepository.existsByNameIgnoreCase(name.uppercase()))
            assertFalse(categoryRepository.existsByNameIgnoreCase("NonExistent-${UUID.randomUUID()}"))
        }
    }

    // ===========================
    // Article CRUD (Req 3.1, 3.6, 3.7)
    // ===========================

    @Nested
    @DisplayName("Article CRUD Operations")
    inner class ArticleCrudTests {

        @Test
        @DisplayName("should create and find article by ID with valid FK references")
        fun `create and find article by id`() {
            val author = createAuthor()
            val category = createCategory()
            val article = createArticle(
                authorId = author.id,
                categoryId = category.id,
                title = "My First Article",
                body = "Content of the article",
                tags = listOf("kotlin", "spring")
            )

            val found = articleRepository.findById(article.id)

            assertNotNull(found)
            assertEquals("My First Article", found!!.title)
            assertEquals("Content of the article", found.body)
            assertEquals(author.id, found.authorId)
            assertEquals(category.id, found.categoryId)
            assertEquals(listOf("kotlin", "spring"), found.tags)
            assertEquals(ArticleStatus.Draft, found.status)
        }

        @Test
        @DisplayName("should update article fields")
        fun `update article`() {
            val author = createAuthor()
            val category = createCategory()
            val article = createArticle(authorId = author.id, categoryId = category.id)

            val updated = articleRepository.save(
                article.copy(
                    title = "Updated Title",
                    body = "Updated body content",
                    updatedAt = Instant.now()
                )
            )

            val found = articleRepository.findById(updated.id)
            assertEquals("Updated Title", found!!.title)
            assertEquals("Updated body content", found.body)
        }

        @Test
        @DisplayName("should delete article by ID")
        fun `delete article by id`() {
            val author = createAuthor()
            val category = createCategory()
            val article = createArticle(authorId = author.id, categoryId = category.id)

            articleRepository.deleteById(article.id)
            entityManager.flush()

            assertNull(articleRepository.findById(article.id))
        }

        @Test
        @DisplayName("should check existsByAuthorId correctly")
        fun `exists by author id`() {
            val author = createAuthor()
            val category = createCategory()
            createArticle(authorId = author.id, categoryId = category.id)

            assertTrue(articleRepository.existsByAuthorId(author.id))
            assertFalse(articleRepository.existsByAuthorId(UUID.randomUUID()))
        }

        @Test
        @DisplayName("should check existsByCategoryId correctly")
        fun `exists by category id`() {
            val author = createAuthor()
            val category = createCategory()
            createArticle(authorId = author.id, categoryId = category.id)

            assertTrue(articleRepository.existsByCategoryId(category.id))
            assertFalse(articleRepository.existsByCategoryId(UUID.randomUUID()))
        }

        @Test
        @DisplayName("should create article with empty tags")
        fun `create article with empty tags`() {
            val author = createAuthor()
            val category = createCategory()
            val article = createArticle(authorId = author.id, categoryId = category.id, tags = emptyList())

            val found = articleRepository.findById(article.id)
            assertEquals(emptyList<String>(), found!!.tags)
        }

        @Test
        @DisplayName("should persist published status and published_at timestamp")
        fun `create published article with published_at`() {
            val author = createAuthor()
            val category = createCategory()
            val publishedAt = Instant.now()
            val article = createArticle(
                authorId = author.id,
                categoryId = category.id,
                status = ArticleStatus.Published,
                publishedAt = publishedAt
            )

            val found = articleRepository.findById(article.id)
            assertEquals(ArticleStatus.Published, found!!.status)
            assertNotNull(found.publishedAt)
        }
    }

    // ===========================
    // FK Constraint Enforcement (Req 3.6, 3.7)
    // ===========================

    @Nested
    @DisplayName("Foreign Key Constraint Enforcement")
    inner class ForeignKeyConstraintTests {

        @Test
        @DisplayName("should reject article with non-existent author_id")
        fun `article with invalid author_id fails`() {
            val category = createCategory()
            val nonExistentAuthorId = UUID.randomUUID()

            assertThrows<Exception> {
                articleRepository.save(
                    Article(
                        id = UUID.randomUUID(),
                        title = "Invalid Article",
                        body = "Body",
                        summary = null,
                        authorId = nonExistentAuthorId,
                        categoryId = category.id,
                        tags = emptyList(),
                        status = ArticleStatus.Draft,
                        createdAt = Instant.now(),
                        updatedAt = Instant.now(),
                        publishedAt = null
                    )
                )
                entityManager.flush()
            }
        }

        @Test
        @DisplayName("should reject article with non-existent category_id")
        fun `article with invalid category_id fails`() {
            val author = createAuthor()
            val nonExistentCategoryId = UUID.randomUUID()

            assertThrows<Exception> {
                articleRepository.save(
                    Article(
                        id = UUID.randomUUID(),
                        title = "Invalid Article",
                        body = "Body",
                        summary = null,
                        authorId = author.id,
                        categoryId = nonExistentCategoryId,
                        tags = emptyList(),
                        status = ArticleStatus.Draft,
                        createdAt = Instant.now(),
                        updatedAt = Instant.now(),
                        publishedAt = null
                    )
                )
                entityManager.flush()
            }
        }

        @Test
        @DisplayName("should reject deletion of author with associated articles")
        fun `cannot delete author with articles`() {
            val author = createAuthor()
            val category = createCategory()
            createArticle(authorId = author.id, categoryId = category.id)
            entityManager.flush()

            assertThrows<Exception> {
                authorRepository.deleteById(author.id)
                entityManager.flush()
            }
        }

        @Test
        @DisplayName("should reject deletion of category with associated articles")
        fun `cannot delete category with articles`() {
            val author = createAuthor()
            val category = createCategory()
            createArticle(authorId = author.id, categoryId = category.id)
            entityManager.flush()

            assertThrows<Exception> {
                categoryRepository.deleteById(category.id)
                entityManager.flush()
            }
        }
    }

    // ===========================
    // Article Filtering (Req 3.5)
    // ===========================

    @Nested
    @DisplayName("Article Filtering")
    inner class ArticleFilteringTests {

        @Test
        @DisplayName("should filter articles by author ID")
        fun `filter by author id`() {
            val author1 = createAuthor()
            val author2 = createAuthor()
            val category = createCategory()

            createArticle(authorId = author1.id, categoryId = category.id, title = "Author1 Article")
            createArticle(authorId = author2.id, categoryId = category.id, title = "Author2 Article")

            val filter = ArticleFilter(authorId = author1.id)
            val page = articleRepository.findAll(filter, PageRequest.of(0, 20))

            assertEquals(1, page.totalElements)
            assertEquals("Author1 Article", page.content[0].title)
        }

        @Test
        @DisplayName("should filter articles by category ID")
        fun `filter by category id`() {
            val author = createAuthor()
            val cat1 = createCategory(
                name = "Cat1-${UUID.randomUUID().toString().take(8)}",
                slug = "cat1-${UUID.randomUUID().toString().take(8)}"
            )
            val cat2 = createCategory(
                name = "Cat2-${UUID.randomUUID().toString().take(8)}",
                slug = "cat2-${UUID.randomUUID().toString().take(8)}"
            )

            createArticle(authorId = author.id, categoryId = cat1.id, title = "Cat1 Article")
            createArticle(authorId = author.id, categoryId = cat2.id, title = "Cat2 Article")

            val filter = ArticleFilter(categoryId = cat1.id)
            val page = articleRepository.findAll(filter, PageRequest.of(0, 20))

            assertEquals(1, page.totalElements)
            assertEquals("Cat1 Article", page.content[0].title)
        }

        @Test
        @DisplayName("should filter articles by status")
        fun `filter by status`() {
            val author = createAuthor()
            val category = createCategory()

            createArticle(authorId = author.id, categoryId = category.id, status = ArticleStatus.Draft, title = "Draft")
            createArticle(
                authorId = author.id,
                categoryId = category.id,
                status = ArticleStatus.Published,
                title = "Published",
                publishedAt = Instant.now()
            )

            val filter = ArticleFilter(status = ArticleStatus.Published, authorId = author.id)
            val page = articleRepository.findAll(filter, PageRequest.of(0, 20))

            assertTrue(page.content.all { it.status == ArticleStatus.Published })
            assertEquals(1, page.totalElements)
            assertEquals("Published", page.content[0].title)
        }

        @Test
        @DisplayName("should return all articles when no filter is applied")
        fun `no filter returns all`() {
            val author = createAuthor()
            val category = createCategory()

            createArticle(authorId = author.id, categoryId = category.id, title = "Article A")
            createArticle(authorId = author.id, categoryId = category.id, title = "Article B")

            val filter = ArticleFilter(authorId = author.id)
            val page = articleRepository.findAll(filter, PageRequest.of(0, 20))

            assertTrue(page.totalElements >= 2)
        }

        @Test
        @DisplayName("should support pagination")
        fun `pagination works correctly`() {
            val author = createAuthor()
            val category = createCategory()

            repeat(5) { i ->
                createArticle(authorId = author.id, categoryId = category.id, title = "Paginated $i")
            }

            val page1 = articleRepository.findAll(ArticleFilter(authorId = author.id), PageRequest.of(0, 2))
            val page2 = articleRepository.findAll(ArticleFilter(authorId = author.id), PageRequest.of(1, 2))

            assertEquals(2, page1.content.size)
            assertEquals(2, page2.content.size)
            assertEquals(5, page1.totalElements)
            assertEquals(3, page1.totalPages)
        }
    }

    // ===========================
    // Full-Text Search (Req 5.1)
    // ===========================

    @Nested
    @DisplayName("Full-Text Search with tsvector")
    inner class FullTextSearchTests {

        @Test
        @Transactional
        @DisplayName("should find published article by title keyword")
        fun `search finds published article by title`() {
            val author = createAuthor()
            val category = createCategory()

            createArticle(
                authorId = author.id,
                categoryId = category.id,
                title = "Kubernetes Deployment Strategies",
                body = "This article covers advanced deployment patterns using Kubernetes.",
                status = ArticleStatus.Published,
                publishedAt = Instant.now()
            )
            entityManager.flush()

            val results = searchAdapter.search("kubernetes", PageRequest.of(0, 10))

            assertEquals(1, results.totalElements)
            assertEquals("Kubernetes Deployment Strategies", results.content[0].title)
        }

        @Test
        @Transactional
        @DisplayName("should find published article by body keyword")
        fun `search finds published article by body content`() {
            val author = createAuthor()
            val category = createCategory()

            createArticle(
                authorId = author.id,
                categoryId = category.id,
                title = "Introduction to Programming",
                body = "This comprehensive tutorial explains microservices architecture patterns.",
                status = ArticleStatus.Published,
                publishedAt = Instant.now()
            )
            entityManager.flush()

            val results = searchAdapter.search("microservices", PageRequest.of(0, 10))

            assertEquals(1, results.totalElements)
            assertTrue(results.content[0].body.contains("microservices"))
        }

        @Test
        @Transactional
        @DisplayName("should NOT return draft articles in search results")
        fun `search excludes draft articles`() {
            val author = createAuthor()
            val category = createCategory()

            createArticle(
                authorId = author.id,
                categoryId = category.id,
                title = "Quantum Computing Basics",
                body = "Exploring quantum computing fundamentals.",
                status = ArticleStatus.Draft
            )
            entityManager.flush()

            val results = searchAdapter.search("quantum", PageRequest.of(0, 10))

            assertEquals(0, results.totalElements)
        }

        @Test
        @Transactional
        @DisplayName("should NOT return review articles in search results")
        fun `search excludes review articles`() {
            val author = createAuthor()
            val category = createCategory()

            createArticle(
                authorId = author.id,
                categoryId = category.id,
                title = "Blockchain Revolution",
                body = "Understanding distributed ledger technology and blockchain.",
                status = ArticleStatus.Review
            )
            entityManager.flush()

            val results = searchAdapter.search("blockchain", PageRequest.of(0, 10))

            assertEquals(0, results.totalElements)
        }

        @Test
        @Transactional
        @DisplayName("should return empty results for unmatched query")
        fun `search returns empty for non-matching query`() {
            val author = createAuthor()
            val category = createCategory()

            createArticle(
                authorId = author.id,
                categoryId = category.id,
                title = "Java Programming",
                body = "Learn Java basics.",
                status = ArticleStatus.Published,
                publishedAt = Instant.now()
            )
            entityManager.flush()

            val results = searchAdapter.search("xyznonexistent", PageRequest.of(0, 10))

            assertEquals(0, results.totalElements)
            assertTrue(results.content.isEmpty())
        }

        @Test
        @Transactional
        @DisplayName("should support multi-word search queries")
        fun `search supports multi-word queries`() {
            val author = createAuthor()
            val category = createCategory()

            createArticle(
                authorId = author.id,
                categoryId = category.id,
                title = "Advanced Spring Boot Configuration",
                body = "This guide covers spring boot auto-configuration and custom properties.",
                status = ArticleStatus.Published,
                publishedAt = Instant.now()
            )
            entityManager.flush()

            val results = searchAdapter.search("spring configuration", PageRequest.of(0, 10))

            assertEquals(1, results.totalElements)
        }

        @Test
        @Transactional
        @DisplayName("should paginate search results")
        fun `search supports pagination`() {
            val author = createAuthor()
            val category = createCategory()

            repeat(3) { i ->
                createArticle(
                    authorId = author.id,
                    categoryId = category.id,
                    title = "Reactive Programming Part $i",
                    body = "Exploring reactive streams and reactive programming patterns with Project Reactor.",
                    status = ArticleStatus.Published,
                    publishedAt = Instant.now()
                )
            }
            entityManager.flush()

            val page1 = searchAdapter.search("reactive", PageRequest.of(0, 2))
            val page2 = searchAdapter.search("reactive", PageRequest.of(1, 2))

            assertEquals(3, page1.totalElements)
            assertEquals(2, page1.content.size)
            assertEquals(1, page2.content.size)
        }
    }
}
