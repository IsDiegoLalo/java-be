package com.platform.content.application.category

import com.platform.content.domain.ConflictException
import com.platform.content.domain.EntityNotFoundException
import com.platform.content.domain.model.Category
import com.platform.content.domain.port.ArticleRepository
import com.platform.content.domain.port.CategoryRepository
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Application service orchestrating category management use cases (SRP).
 *
 * Handles creation, retrieval, update, and deletion of categories with:
 * - Name validation (delegated to CategoryValidator)
 * - Slug auto-generation (delegated to SlugGenerator)
 * - Name and slug uniqueness enforcement (case-insensitive for name)
 * - Deletion guard preventing removal of categories with associated articles
 *
 * Depends on abstractions (DIP): CategoryRepository, ArticleRepository ports.
 */
@Service
class CategoryService(
    private val categoryRepository: CategoryRepository,
    private val articleRepository: ArticleRepository,
    private val categoryValidator: CategoryValidator
) {

    /**
     * Creates a new category after validation and uniqueness checks.
     *
     * @param name the category name (2–100 characters, non-blank)
     * @param description optional description (max 500 characters)
     * @return the persisted Category entity
     * @throws com.platform.content.domain.ValidationException if name/description validation fails
     * @throws ConflictException if name (case-insensitive) or generated slug already exists
     */
    fun create(name: String, description: String? = null): Category {
        categoryValidator.validate(name, description)

        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw ConflictException("Category", "A category with name '$name' already exists")
        }

        val slug = SlugGenerator.generate(name)

        if (categoryRepository.existsBySlug(slug)) {
            throw ConflictException("Category", "A category with slug '$slug' already exists")
        }

        val category = Category(
            id = UUID.randomUUID(),
            name = name,
            description = description,
            slug = slug
        )

        return categoryRepository.save(category)
    }

    /**
     * Retrieves a category by its identifier.
     *
     * @param id the category UUID
     * @return the Category entity
     * @throws EntityNotFoundException if no category exists with the given id
     */
    fun findById(id: UUID): Category {
        return categoryRepository.findById(id)
            ?: throw EntityNotFoundException("Category", id)
    }

    /**
     * Updates an existing category's name and description.
     *
     * Re-validates name, regenerates slug, and checks uniqueness excluding the current category.
     *
     * @param id the category UUID
     * @param name the new category name
     * @param description the new optional description
     * @return the updated Category entity
     * @throws EntityNotFoundException if no category exists with the given id
     * @throws com.platform.content.domain.ValidationException if name/description validation fails
     * @throws ConflictException if updated name or slug conflicts with another category
     */
    fun update(id: UUID, name: String, description: String? = null): Category {
        val existing = categoryRepository.findById(id)
            ?: throw EntityNotFoundException("Category", id)

        categoryValidator.validate(name, description)

        checkNameUniqueness(name, excludeId = id)

        val slug = SlugGenerator.generate(name)
        checkSlugUniqueness(slug, excludeId = id)

        val updated = existing.copy(
            name = name,
            description = description,
            slug = slug
        )

        return categoryRepository.save(updated)
    }

    /**
     * Deletes a category if it has no associated articles.
     *
     * @param id the category UUID
     * @throws EntityNotFoundException if no category exists with the given id
     * @throws ConflictException if one or more articles are assigned to this category
     */
    fun delete(id: UUID) {
        categoryRepository.findById(id)
            ?: throw EntityNotFoundException("Category", id)

        if (articleRepository.existsByCategoryId(id)) {
            throw ConflictException("Category", "Cannot delete category that has associated articles")
        }

        categoryRepository.deleteById(id)
    }

    /**
     * Checks name uniqueness (case-insensitive), optionally excluding a specific category
     * to allow updating a category without conflicting with itself.
     */
    private fun checkNameUniqueness(name: String, excludeId: UUID) {
        val existingByName = categoryRepository.findByNameIgnoreCase(name)
        if (existingByName != null && existingByName.id != excludeId) {
            throw ConflictException("Category", "A category with name '$name' already exists")
        }
    }

    /**
     * Checks slug uniqueness, optionally excluding a specific category
     * to allow updating a category without conflicting with itself.
     */
    private fun checkSlugUniqueness(slug: String, excludeId: UUID) {
        val existingBySlug = categoryRepository.findBySlug(slug)
        if (existingBySlug != null && existingBySlug.id != excludeId) {
            throw ConflictException("Category", "A category with slug '$slug' already exists")
        }
    }
}
