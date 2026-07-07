package com.platform.content.domain.model

import java.time.Instant
import java.util.UUID

data class ArticlePublishedEvent(
    val articleId: UUID,
    val title: String,
    val authorId: UUID,
    val category: String,
    val tags: List<String>,
    val publishedAt: Instant
)
