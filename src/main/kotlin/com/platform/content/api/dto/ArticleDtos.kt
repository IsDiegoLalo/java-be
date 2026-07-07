package com.platform.content.api.dto

import com.platform.content.domain.model.Article
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Request DTO for creating a new article.
 */
data class CreateArticleRequest(
    @field:NotBlank(message = "Title must not be blank")
    @field:Size(min = 1, max = 255, message = "Title must be between 1 and 255 characters")
    val title: String,

    @field:NotBlank(message = "Body must not be blank")
    val body: String,

    @field:Size(max = 500, message = "Summary must not exceed 500 characters")
    val summary: String? = null,

    @field:NotNull(message = "Author ID is required")
    val authorId: UUID,

    @field:NotNull(message = "Category ID is required")
    val categoryId: UUID,

    @field:Size(max = 10, message = "Tags must not exceed 10 entries")
    val tags: List<String> = emptyList()
)

/**
 * Request DTO for updating an existing article.
 */
data class UpdateArticleRequest(
    @field:NotBlank(message = "Title must not be blank")
    @field:Size(min = 1, max = 255, message = "Title must be between 1 and 255 characters")
    val title: String,

    @field:NotBlank(message = "Body must not be blank")
    val body: String,

    @field:Size(max = 500, message = "Summary must not exceed 500 characters")
    val summary: String? = null,

    @field:NotNull(message = "Author ID is required")
    val authorId: UUID,

    @field:NotNull(message = "Category ID is required")
    val categoryId: UUID,

    @field:Size(max = 10, message = "Tags must not exceed 10 entries")
    val tags: List<String> = emptyList()
)

/**
 * Request DTO for transitioning article status.
 */
data class TransitionStatusRequest(
    @field:NotBlank(message = "Target status must not be blank")
    val targetStatus: String
)

/**
 * Response DTO representing an article.
 * Status is serialized as its string value.
 * Dates are formatted as ISO 8601 strings (UTC).
 */
data class ArticleResponse(
    val id: UUID,
    val title: String,
    val body: String,
    val summary: String?,
    val authorId: UUID,
    val categoryId: UUID,
    val tags: List<String>,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
    val publishedAt: String?
) {
    companion object {
        private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC)

        fun fromDomain(article: Article): ArticleResponse = ArticleResponse(
            id = article.id,
            title = article.title,
            body = article.body,
            summary = article.summary,
            authorId = article.authorId,
            categoryId = article.categoryId,
            tags = article.tags,
            status = article.status.value,
            createdAt = formatter.format(article.createdAt),
            updatedAt = formatter.format(article.updatedAt),
            publishedAt = article.publishedAt?.let { formatter.format(it) }
        )
    }
}
