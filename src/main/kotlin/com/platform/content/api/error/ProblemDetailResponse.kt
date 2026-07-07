package com.platform.content.api.error

import com.platform.content.domain.FieldError

/**
 * RFC 7807 Problem Details response structure.
 * Provides a consistent error response format across all API endpoints.
 */
data class ProblemDetailResponse(
    val type: String,
    val title: String,
    val status: Int,
    val detail: String,
    val instance: String,
    val fieldErrors: List<FieldErrorResponse>? = null
)

/**
 * Field-level error detail for validation failures.
 */
data class FieldErrorResponse(
    val field: String,
    val message: String
) {
    companion object {
        fun fromDomain(fieldError: FieldError): FieldErrorResponse =
            FieldErrorResponse(field = fieldError.field, message = fieldError.message)
    }
}
