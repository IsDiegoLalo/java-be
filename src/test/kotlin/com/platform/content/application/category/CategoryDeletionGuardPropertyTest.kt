package com.platform.content.application.category

import com.platform.content.domain.ConflictException
import com.platform.content.domain.model.Category
import com.platform.content.domain.port.ArticleRepository
import com.platform.content.domain.port.CategoryRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.jqwik.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import java.util.UUID

/**
 * Property-based test for Category deletion guard.
 *
 * **Validates: Requirements 2.3**
 *
 * Property 6: Category deletion guard
 * For any category that has one or more articles assigned to it, attempting to
 * delete that category should always be rejected with an error indicating the
 * category is in use.
 */
@Tag("Feature: content-publishing-platform, Property 6: Category deletion guard")
class CategoryDeletionGuardPropertyTest {

    /**
     * For any random category ID, if the category exists and has associated articles,
     * deletion must always be rejected with a ConflictException with entityType "Category".
     */
    @Property(tries = 100)
    fun `should always reject deletion when category has assigned articles`(
        @ForAll("randomUUIDs") categoryId: UUID
    ) {
        // Arrange
        val categoryRepository: CategoryRepository = mockk()
        val articleRepository: ArticleRepository = mockk()
        val categoryValidator = CategoryValidator()
        val service = CategoryService(categoryRepository, articleRepository, categoryValidator)

        val category = Category(
            id = categoryId,
            name = "Category-${categoryId.toString().take(8)}",
            description = "Test category",
            slug = "category-${categoryId.toString().take(8).lowercase()}"
        )

        every { categoryRepository.findById(categoryId) } returns category
        every { articleRepository.existsByCategoryId(categoryId) } returns true

        // Act & Assert
        val exception = assertThrows<ConflictException> {
            service.delete(categoryId)
        }

        assertEquals("Category", exception.entityType)

        // Verify that deleteById was never called
        verify(exactly = 0) { categoryRepository.deleteById(any()) }
    }

    // --- Generators ---

    @Provide
    fun randomUUIDs(): Arbitrary<UUID> {
        return Arbitraries.create { UUID.randomUUID() }
    }
}
