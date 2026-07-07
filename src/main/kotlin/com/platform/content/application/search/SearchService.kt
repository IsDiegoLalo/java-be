package com.platform.content.application.search

import com.platform.content.domain.FieldError
import com.platform.content.domain.ValidationException
import com.platform.content.domain.model.Article
import com.platform.content.domain.port.ArticleSearchPort
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

/**
 * Application service orchestrating full-text search use cases (SRP).
 *
 * Validates search query constraints before delegating to the ArticleSearchPort
 * abstraction (DIP). The infrastructure adapter behind ArticleSearchPort handles
 * PostgreSQL tsvector indexing and ensures only published articles are returned.
 *
 * Validation rules:
 * - Query must not be blank or whitespace-only (requirement 5.6)
 * - Query must not exceed 200 characters (requirement 5.7)
 */
@Service
class SearchService(
    private val articleSearchPort: ArticleSearchPort
) {

    /**
     * Searches published articles by the given query string with pagination.
     *
     * @param query the search term to match against article title and body
     * @param pageable pagination parameters (page number, page size)
     * @return a page of articles matching the search query
     * @throws ValidationException if the query is blank or exceeds 200 characters
     */
    fun search(query: String, pageable: Pageable): Page<Article> {
        validateQuery(query)
        return articleSearchPort.search(query, pageable)
    }

    private fun validateQuery(query: String) {
        if (query.isBlank()) {
            throw ValidationException(
                listOf(FieldError("query", "Search term must not be empty"))
            )
        }
        if (query.length > 200) {
            throw ValidationException(
                listOf(FieldError("query", "Search term must not exceed 200 characters"))
            )
        }
    }
}
