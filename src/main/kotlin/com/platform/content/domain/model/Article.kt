package com.platform.content.domain.model

import java.time.Instant
import java.util.UUID

data class Article(
    val id: UUID,
    val title: String,
    val body: String,
    val summary: String?,
    val authorId: UUID,
    val categoryId: UUID,
    val tags: List<String>,
    val status: ArticleStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val publishedAt: Instant?
)
