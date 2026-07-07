package com.platform.content.api.dto

import com.platform.content.domain.model.Author
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Request DTO for creating a new author.
 */
data class CreateAuthorRequest(
    @field:NotBlank(message = "Name must not be blank")
    @field:Size(min = 1, max = 100, message = "Name must be between 1 and 100 characters")
    val name: String,

    @field:NotBlank(message = "Email must not be blank")
    @field:Email(message = "Email must be a valid email address")
    @field:Size(max = 255, message = "Email must not exceed 255 characters")
    val email: String,

    @field:Size(max = 500, message = "Bio must not exceed 500 characters")
    val bio: String? = null
)

/**
 * Request DTO for updating an existing author.
 */
data class UpdateAuthorRequest(
    @field:NotBlank(message = "Name must not be blank")
    @field:Size(min = 1, max = 100, message = "Name must be between 1 and 100 characters")
    val name: String,

    @field:NotBlank(message = "Email must not be blank")
    @field:Email(message = "Email must be a valid email address")
    @field:Size(max = 255, message = "Email must not exceed 255 characters")
    val email: String,

    @field:Size(max = 500, message = "Bio must not exceed 500 characters")
    val bio: String? = null
)

/**
 * Response DTO representing an author.
 * Dates are formatted as ISO 8601 strings (UTC).
 */
data class AuthorResponse(
    val id: UUID,
    val name: String,
    val email: String,
    val bio: String?,
    val createdAt: String
) {
    companion object {
        private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC)

        fun fromDomain(author: Author): AuthorResponse = AuthorResponse(
            id = author.id,
            name = author.name,
            email = author.email,
            bio = author.bio,
            createdAt = formatter.format(author.createdAt)
        )
    }
}
