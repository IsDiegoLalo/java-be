package com.platform.content.domain.port

import com.platform.content.domain.model.Article
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/**
 * Port for full-text search over articles.
 * Separated from ArticleRepository per ISP — search is a distinct concern
 * with its own implementation (e.g., PostgreSQL tsvector adapter).
 */
interface ArticleSearchPort {
    fun search(query: String, pageable: Pageable): Page<Article>
}
