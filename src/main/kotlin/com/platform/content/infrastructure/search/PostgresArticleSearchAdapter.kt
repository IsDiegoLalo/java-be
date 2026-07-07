package com.platform.content.infrastructure.search

import com.platform.content.domain.model.Article
import com.platform.content.domain.model.ArticleStatus
import com.platform.content.domain.port.ArticleSearchPort
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/**
 * PostgreSQL full-text search adapter implementing ArticleSearchPort.
 * Uses native SQL with ts_rank and to_tsquery for relevance-ranked search
 * over the search_vector tsvector column maintained by a database trigger.
 *
 * Only returns articles with status = 'published'.
 */
// Adapter Pattern - translates between domain search port and PostgreSQL tsvector
@Repository
class PostgresArticleSearchAdapter(
    @PersistenceContext private val entityManager: EntityManager
) : ArticleSearchPort {

    override fun search(query: String, pageable: Pageable): Page<Article> {
        val tsQuery = sanitizeQuery(query)

        val countSql = """
            SELECT COUNT(*)
            FROM articles
            WHERE status = 'published'
              AND search_vector @@ to_tsquery('english', :query)
        """.trimIndent()

        val totalElements = entityManager.createNativeQuery(countSql)
            .setParameter("query", tsQuery)
            .singleResult.let { (it as Number).toLong() }

        if (totalElements == 0L) {
            return PageImpl(emptyList(), pageable, 0L)
        }

        val searchSql = """
            SELECT id, title, body, summary, author_id, category_id, tags, status,
                   created_at, updated_at, published_at,
                   ts_rank(search_vector, to_tsquery('english', :query)) AS relevance
            FROM articles
            WHERE status = 'published'
              AND search_vector @@ to_tsquery('english', :query)
            ORDER BY relevance DESC
            LIMIT :limit OFFSET :offset
        """.trimIndent()

        @Suppress("UNCHECKED_CAST")
        val results = entityManager.createNativeQuery(searchSql)
            .setParameter("query", tsQuery)
            .setParameter("limit", pageable.pageSize)
            .setParameter("offset", pageable.offset.toInt())
            .resultList as List<Array<Any?>>

        val articles = results.map { row -> mapRowToArticle(row) }

        return PageImpl(articles, pageable, totalElements)
    }

    /**
     * Sanitizes the user query for use with to_tsquery.
     * Splits on whitespace and joins with '&' for AND semantics.
     * Removes special tsquery characters to prevent syntax errors.
     */
    private fun sanitizeQuery(query: String): String {
        return query.trim()
            .replace(Regex("[^\\w\\s]"), "")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" & ")
    }

    /**
     * Maps a native query result row to an Article domain model.
     */
    private fun mapRowToArticle(row: Array<Any?>): Article {
        return Article(
            id = row[0] as UUID,
            title = row[1] as String,
            body = row[2] as String,
            summary = row[3] as String?,
            authorId = row[4] as UUID,
            categoryId = row[5] as UUID,
            tags = parseTagsArray(row[6]),
            status = ArticleStatus.fromString(row[7] as String),
            createdAt = toInstant(row[8]!!),
            updatedAt = toInstant(row[9]!!),
            publishedAt = row[10]?.let { toInstant(it) }
        )
    }

    /**
     * Parses PostgreSQL text[] array from native query result into a List<String>.
     */
    private fun parseTagsArray(value: Any?): List<String> {
        if (value == null) return emptyList()
        return when (value) {
            is Array<*> -> value.filterNotNull().map { it.toString() }
            is java.sql.Array -> {
                val array = value.array
                if (array is Array<*>) {
                    array.filterNotNull().map { it.toString() }
                } else {
                    emptyList()
                }
            }
            is String -> parsePostgresArrayString(value)
            else -> emptyList()
        }
    }

    /**
     * Parses a PostgreSQL array string format like {tag1,tag2} into a list.
     */
    private fun parsePostgresArrayString(value: String): List<String> {
        if (value == "{}" || value.isBlank()) return emptyList()
        val content = value.removeSurrounding("{", "}")
        if (content.isEmpty()) return emptyList()
        return content.split(",").map { it.trim().removeSurrounding("\"") }
    }

    /**
     * Converts a database timestamp value to an Instant.
     */
    private fun toInstant(value: Any): Instant {
        return when (value) {
            is Instant -> value
            is java.sql.Timestamp -> value.toInstant()
            is java.time.OffsetDateTime -> value.toInstant()
            else -> Instant.parse(value.toString())
        }
    }
}
