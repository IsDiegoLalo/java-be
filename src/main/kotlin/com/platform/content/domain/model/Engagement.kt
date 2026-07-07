package com.platform.content.domain.model

import java.util.UUID

data class EngagementRecord(
    val articleId: UUID,
    val pageViews: Long,
    val averageReadTimeSeconds: Double,
    val interactions: InteractionCounts
)

data class InteractionCounts(
    val likes: Long,
    val shares: Long,
    val comments: Long
)

data class AggregatedEngagement(
    val authorId: UUID,
    val totalPageViews: Long,
    val averageReadTimeSeconds: Double,
    val totalInteractions: InteractionCounts
)

enum class InteractionType {
    LIKE, SHARE, COMMENT
}
