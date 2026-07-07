package com.platform.content.api.dto

import com.platform.content.domain.model.Category
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

/**
 * Request DTO for creating a new category.
 */
data class CreateCategoryRequest(
    @field:NotBlank(message = "Name must not be blank")
    @field:Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    val name: String,

    @field:Size(max = 500, message = "Description must not exceed 500 characters")
    val description: String? = null
)

/**
 * Request DTO for updating an existing category.
 */
data class UpdateCategoryRequest(
    @field:NotBlank(message = "Name must not be blank")
    @field:Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    val name: String,

    @field:Size(max = 500, message = "Description must not exceed 500 characters")
    val description: String? = null
)

/**
 * Response DTO representing a category.
 */
data class CategoryResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val slug: String
) {
    companion object {
        fun fromDomain(category: Category): CategoryResponse = CategoryResponse(
            id = category.id,
            name = category.name,
            description = category.description,
            slug = category.slug
        )
    }
}
