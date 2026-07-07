package com.platform.content.domain.port

import com.platform.content.domain.model.ArticleStatus
import java.util.UUID

/**
 * Filter criteria for article listing queries.
 * All fields are optional — only non-null fields are applied as filter conditions.
 */
data class ArticleFilter(
    val authorId: UUID? = null,
    val categoryId: UUID? = null,
    val status: ArticleStatus? = null,
    val tags: List<String>? = null
)
