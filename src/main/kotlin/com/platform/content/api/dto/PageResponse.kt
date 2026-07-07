package com.platform.content.api.dto

/**
 * Generic pagination wrapper for list responses.
 * Provides pagination metadata alongside the content.
 */
data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
) {
    companion object {
        /**
         * Creates a PageResponse from a Spring Data Page object,
         * applying the given mapper to transform each element.
         */
        fun <T, R> fromPage(
            page: org.springframework.data.domain.Page<T>,
            mapper: (T) -> R
        ): PageResponse<R> = PageResponse(
            content = page.content.map(mapper),
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages
        )

        /**
         * Creates a PageResponse directly from content and pagination metadata.
         */
        fun <T> of(
            content: List<T>,
            page: Int,
            size: Int,
            totalElements: Long
        ): PageResponse<T> = PageResponse(
            content = content,
            page = page,
            size = size,
            totalElements = totalElements,
            totalPages = if (size > 0) ((totalElements + size - 1) / size).toInt() else 0
        )
    }
}
