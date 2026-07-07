package com.platform.content.application.category

import com.platform.content.domain.ConflictException
import com.platform.content.domain.EntityNotFoundException
import com.platform.content.domain.ValidationException
import com.platform.content.domain.model.Category
import com.platform.content.domain.port.ArticleRepository
import com.platform.content.domain.port.CategoryRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class CategoryServiceTest {

    private val categoryRepository: CategoryRepository = mockk()
    private val articleRepository: ArticleRepository = mockk()
    private val categoryValidator: CategoryValidator = CategoryValidator()

    private lateinit var service: CategoryService

    private val existingCategoryId = UUID.randomUUID()
    private val nonExistentCategoryId = UUID.randomUUID()
    private val existingCategory = Category(
        id = existingCategoryId,
        name = "Technology",
        description = "Tech articles",
        slug = "technology"
    )

    @BeforeEach
    fun setUp() {
        service = CategoryService(categoryRepository, articleRepository, categoryValidator)

        every { categoryRepository.findById(existingCategoryId) } returns existingCategory
        every { categoryRepository.findById(nonExistentCategoryId) } returns null
    }

    // --- Create ---

    @Test
    fun `should create category with valid name and description`() {
        every { categoryRepository.existsByNameIgnoreCase("Science") } returns false
        every { categoryRepository.existsBySlug("science") } returns false
        val categorySlot = slot<Category>()
        every { categoryRepository.save(capture(categorySlot)) } answers { categorySlot.captured }

        val result = service.create("Science", "Scientific articles")

        assertEquals("Science", result.name)
        assertEquals("Scientific articles", result.description)
        assertEquals("science", result.slug)
        assertNotNull(result.id)
    }

    @Test
    fun `should create category without description`() {
        every { categoryRepository.existsByNameIgnoreCase("Science") } returns false
        every { categoryRepository.existsBySlug("science") } returns false
        val categorySlot = slot<Category>()
        every { categoryRepository.save(capture(categorySlot)) } answers { categorySlot.captured }

        val result = service.create("Science")

        assertEquals("Science", result.name)
        assertEquals(null, result.description)
        assertEquals("science", result.slug)
    }

    @Test
    fun `should throw ValidationException for blank name on create`() {
        assertThrows<ValidationException> {
            service.create("   ")
        }
    }

    @Test
    fun `should throw ValidationException for name too short on create`() {
        assertThrows<ValidationException> {
            service.create("A")
        }
    }

    @Test
    fun `should throw ConflictException when name already exists on create`() {
        every { categoryRepository.existsByNameIgnoreCase("Technology") } returns true

        val ex = assertThrows<ConflictException> {
            service.create("Technology")
        }
        assertEquals("Category", ex.entityType)
    }

    @Test
    fun `should throw ConflictException when slug already exists on create`() {
        every { categoryRepository.existsByNameIgnoreCase("Science") } returns false
        every { categoryRepository.existsBySlug("science") } returns true

        val ex = assertThrows<ConflictException> {
            service.create("Science")
        }
        assertEquals("Category", ex.entityType)
    }

    // --- FindById ---

    @Test
    fun `should return category when found by id`() {
        val result = service.findById(existingCategoryId)

        assertEquals(existingCategory, result)
    }

    @Test
    fun `should throw EntityNotFoundException when not found by id`() {
        val ex = assertThrows<EntityNotFoundException> {
            service.findById(nonExistentCategoryId)
        }
        assertEquals("Category", ex.entityType)
        assertEquals(nonExistentCategoryId, ex.entityId)
    }

    // --- Update ---

    @Test
    fun `should update category with new name and description`() {
        every { categoryRepository.findByNameIgnoreCase("Updated Tech") } returns null
        every { categoryRepository.findBySlug("updated-tech") } returns null
        val categorySlot = slot<Category>()
        every { categoryRepository.save(capture(categorySlot)) } answers { categorySlot.captured }

        val result = service.update(existingCategoryId, "Updated Tech", "Updated description")

        assertEquals(existingCategoryId, result.id)
        assertEquals("Updated Tech", result.name)
        assertEquals("Updated description", result.description)
        assertEquals("updated-tech", result.slug)
    }

    @Test
    fun `should allow update with same name for same category`() {
        every { categoryRepository.findByNameIgnoreCase("Technology") } returns existingCategory
        every { categoryRepository.findBySlug("technology") } returns existingCategory
        val categorySlot = slot<Category>()
        every { categoryRepository.save(capture(categorySlot)) } answers { categorySlot.captured }

        val result = service.update(existingCategoryId, "Technology", "New description")

        assertEquals("Technology", result.name)
        assertEquals("New description", result.description)
    }

    @Test
    fun `should throw ConflictException when updating name to existing name of another category`() {
        val otherCategory = Category(
            id = UUID.randomUUID(),
            name = "Science",
            description = null,
            slug = "science"
        )
        every { categoryRepository.findByNameIgnoreCase("Science") } returns otherCategory

        val ex = assertThrows<ConflictException> {
            service.update(existingCategoryId, "Science")
        }
        assertEquals("Category", ex.entityType)
    }

    @Test
    fun `should throw ConflictException when updated slug conflicts with another category`() {
        every { categoryRepository.findByNameIgnoreCase("Science") } returns null
        val otherCategory = Category(
            id = UUID.randomUUID(),
            name = "Old Science",
            description = null,
            slug = "science"
        )
        every { categoryRepository.findBySlug("science") } returns otherCategory

        val ex = assertThrows<ConflictException> {
            service.update(existingCategoryId, "Science")
        }
        assertEquals("Category", ex.entityType)
    }

    @Test
    fun `should throw EntityNotFoundException when updating non-existent category`() {
        val ex = assertThrows<EntityNotFoundException> {
            service.update(nonExistentCategoryId, "NewName")
        }
        assertEquals("Category", ex.entityType)
        assertEquals(nonExistentCategoryId, ex.entityId)
    }

    @Test
    fun `should throw ValidationException for invalid name on update`() {
        assertThrows<ValidationException> {
            service.update(existingCategoryId, "")
        }
    }

    // --- Delete ---

    @Test
    fun `should delete category with no associated articles`() {
        every { articleRepository.existsByCategoryId(existingCategoryId) } returns false
        every { categoryRepository.deleteById(existingCategoryId) } returns Unit

        service.delete(existingCategoryId)

        verify(exactly = 1) { categoryRepository.deleteById(existingCategoryId) }
    }

    @Test
    fun `should throw ConflictException when deleting category with articles`() {
        every { articleRepository.existsByCategoryId(existingCategoryId) } returns true

        val ex = assertThrows<ConflictException> {
            service.delete(existingCategoryId)
        }
        assertEquals("Category", ex.entityType)
    }

    @Test
    fun `should throw EntityNotFoundException when deleting non-existent category`() {
        val ex = assertThrows<EntityNotFoundException> {
            service.delete(nonExistentCategoryId)
        }
        assertEquals("Category", ex.entityType)
        assertEquals(nonExistentCategoryId, ex.entityId)
    }
}
