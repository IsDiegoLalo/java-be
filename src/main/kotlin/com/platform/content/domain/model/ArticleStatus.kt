package com.platform.content.domain.model

/**
 * Type-safe representation of article editorial workflow states.
 * Uses sealed class to ensure exhaustive matching in `when` expressions.
 */
sealed class ArticleStatus(val value: String) {
    data object Draft : ArticleStatus("draft")
    data object Review : ArticleStatus("review")
    data object Published : ArticleStatus("published")

    companion object {
        fun fromString(value: String): ArticleStatus = when (value.lowercase()) {
            "draft" -> Draft
            "review" -> Review
            "published" -> Published
            else -> throw IllegalArgumentException("Unknown status: $value")
        }
    }
}
