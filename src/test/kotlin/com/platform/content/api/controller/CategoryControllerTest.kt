package com.platform.content.api.controller

import com.platform.content.api.dto.CreateCategoryRequest
import com.platform.content.api.dto.UpdateCategoryRequest
import com.platform.content.application.category.CategoryService
import com.platform.content.domain.ConflictException
import com.platform.content.domain.EntityNotFoundException
import com.platform.content.domain.model.Category
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.UUID

class CategoryControllerTest {

    private lateinit var categoryService: CategoryService
    private lateinit var controller: CategoryController

    @BeforeEach
    fun setUp() {
        categoryService = mockk()
        controller = CategoryController(categoryService)
    }

    @Test
    fun `create should return 201 with category response`() {
        val request = CreateCategoryRequest(
            name = "Technology",
            description = "Tech articles"
        )
        val category = Category(
            id = UUID.randomUUID(),
            name = "Technology",
            description = "Tech articles",
            slug = "technology"
        )
        every { categoryService.create("Technology", "Tech articles") } returns category

        val response = controller.create(request)

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val body = response.body!!
        assertEquals(category.id, body.id)
        assertEquals("Technology", body.name)
        assertEquals("Tech articles", body.description)
        assertEquals("technology", body.slug)
    }

    @Test
    fun `create should pass null description when not provided`() {
        val request = CreateCategoryRequest(
            name = "Science",
            description = null
        )
        val category = Category(
            id = UUID.randomUUID(),
            name = "Science",
            description = null,
            slug = "science"
        )
        every { categoryService.create("Science", null) } returns category

        val response = controller.create(request)

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertNull(response.body!!.description)
        verify { categoryService.create("Science", null) }
    }

    @Test
    fun `getById should return 200 with category response`() {
        val categoryId = UUID.randomUUID()
        val category = Category(
            id = categoryId,
            name = "Technology",
            description = "Tech articles",
            slug = "technology"
        )
        every { categoryService.findById(categoryId) } returns category

        val response = controller.getById(categoryId)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals(categoryId, body.id)
        assertEquals("Technology", body.name)
        assertEquals("Tech articles", body.description)
        assertEquals("technology", body.slug)
    }

    @Test
    fun `getById should propagate EntityNotFoundException`() {
        val categoryId = UUID.randomUUID()
        every { categoryService.findById(categoryId) } throws EntityNotFoundException("Category", categoryId)

        val ex = org.junit.jupiter.api.assertThrows<EntityNotFoundException> {
            controller.getById(categoryId)
        }
        assertEquals("Category with id $categoryId not found", ex.message)
    }

    @Test
    fun `update should return 200 with updated category response`() {
        val categoryId = UUID.randomUUID()
        val request = UpdateCategoryRequest(
            name = "Tech Updated",
            description = "Updated description"
        )
        val updatedCategory = Category(
            id = categoryId,
            name = "Tech Updated",
            description = "Updated description",
            slug = "tech-updated"
        )
        every {
            categoryService.update(categoryId, "Tech Updated", "Updated description")
        } returns updatedCategory

        val response = controller.update(categoryId, request)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals(categoryId, body.id)
        assertEquals("Tech Updated", body.name)
        assertEquals("Updated description", body.description)
        assertEquals("tech-updated", body.slug)
    }

    @Test
    fun `update should propagate EntityNotFoundException for non-existent category`() {
        val categoryId = UUID.randomUUID()
        val request = UpdateCategoryRequest(
            name = "Updated",
            description = null
        )
        every {
            categoryService.update(categoryId, "Updated", null)
        } throws EntityNotFoundException("Category", categoryId)

        val ex = org.junit.jupiter.api.assertThrows<EntityNotFoundException> {
            controller.update(categoryId, request)
        }
        assertEquals("Category with id $categoryId not found", ex.message)
    }

    @Test
    fun `update should propagate ConflictException for duplicate name`() {
        val categoryId = UUID.randomUUID()
        val request = UpdateCategoryRequest(
            name = "Existing",
            description = null
        )
        every {
            categoryService.update(categoryId, "Existing", null)
        } throws ConflictException("Category", "A category with name 'Existing' already exists")

        val ex = org.junit.jupiter.api.assertThrows<ConflictException> {
            controller.update(categoryId, request)
        }
        assertEquals("Category conflict: A category with name 'Existing' already exists", ex.message)
    }

    @Test
    fun `delete should return 204 No Content`() {
        val categoryId = UUID.randomUUID()
        every { categoryService.delete(categoryId) } returns Unit

        val response = controller.delete(categoryId)

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        assertNull(response.body)
        verify { categoryService.delete(categoryId) }
    }

    @Test
    fun `delete should propagate ConflictException when category has articles`() {
        val categoryId = UUID.randomUUID()
        every {
            categoryService.delete(categoryId)
        } throws ConflictException("Category", "Cannot delete category that has associated articles")

        val ex = org.junit.jupiter.api.assertThrows<ConflictException> {
            controller.delete(categoryId)
        }
        assertEquals("Category conflict: Cannot delete category that has associated articles", ex.message)
    }

    @Test
    fun `delete should propagate EntityNotFoundException for non-existent category`() {
        val categoryId = UUID.randomUUID()
        every { categoryService.delete(categoryId) } throws EntityNotFoundException("Category", categoryId)

        val ex = org.junit.jupiter.api.assertThrows<EntityNotFoundException> {
            controller.delete(categoryId)
        }
        assertEquals("Category with id $categoryId not found", ex.message)
    }
}
