package com.platform.content.infrastructure.persistence

import com.platform.content.domain.model.Article
import com.platform.content.domain.model.ArticleStatus
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/**
 * JPA entity mapping for the articles table.
 * Uses database-level UUID generation (gen_random_uuid()).
 * Tags are stored as PostgreSQL TEXT[] using Hibernate's native array type support.
 * Status is stored as VARCHAR and converted via ArticleStatusConverter.
 */
@Entity
@Table(name = "articles")
class ArticleEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "title", nullable = false, length = 255)
    var title: String = "",

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    var body: String = "",

    @Column(name = "summary", length = 500)
    var summary: String? = null,

    @Column(name = "author_id", nullable = false)
    var authorId: UUID = UUID.randomUUID(),

    @Column(name = "category_id", nullable = false)
    var categoryId: UUID = UUID.randomUUID(),

    @Column(name = "tags", nullable = false, columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    var tags: List<String> = emptyList(),

    @Column(name = "status", nullable = false, length = 20)
    @Convert(converter = ArticleStatusConverter::class)
    var status: ArticleStatus = ArticleStatus.Draft,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "published_at")
    var publishedAt: Instant? = null
) {
    /**
     * Converts this JPA entity to the domain model.
     */
    fun toDomain(): Article = Article(
        id = id,
        title = title,
        body = body,
        summary = summary,
        authorId = authorId,
        categoryId = categoryId,
        tags = tags,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
        publishedAt = publishedAt
    )

    companion object {
        /**
         * Creates a JPA entity from the domain model.
         */
        fun fromDomain(article: Article): ArticleEntity = ArticleEntity(
            id = article.id,
            title = article.title,
            body = article.body,
            summary = article.summary,
            authorId = article.authorId,
            categoryId = article.categoryId,
            tags = article.tags,
            status = article.status,
            createdAt = article.createdAt,
            updatedAt = article.updatedAt,
            publishedAt = article.publishedAt
        )
    }
}
