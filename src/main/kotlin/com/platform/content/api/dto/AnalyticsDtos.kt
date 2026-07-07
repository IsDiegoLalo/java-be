package com.platform.content.api.dto

import com.platform.content.domain.model.AggregatedEngagement
import com.platform.content.domain.model.EngagementRecord
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import java.util.UUID

/**
 * Request DTO for recording a page view event.
 */
data class PageViewRequest(
    @field:NotNull(message = "Article ID is required")
    val articleId: UUID
)

/**
 * Request DTO for recording read time.
 */
data class ReadTimeRequest(
    @field:NotNull(message = "Article ID is required")
    val articleId: UUID,

    @field:NotNull(message = "Seconds is required")
    @field:Min(value = 1, message = "Seconds must be at least 1")
    @field:Max(value = 3600, message = "Seconds must not exceed 3600")
    val seconds: Int
)

/**
 * Request DTO for recording an interaction event.
 */
data class InteractionRequest(
    @field:NotNull(message = "Article ID is required")
    val articleId: UUID,

    @field:NotBlank(message = "Type must not be blank")
    @field:Pattern(regexp = "LIKE|SHARE|COMMENT", message = "Type must be one of: LIKE, SHARE, COMMENT")
    val type: String
)

/**
 * Response DTO representing engagement metrics for a single article.
 */
data class EngagementResponse(
    val articleId: UUID,
    val pageViews: Long,
    val averageReadTimeSeconds: Double,
    val likes: Long,
    val shares: Long,
    val comments: Long
) {
    companion object {
        fun fromDomain(record: EngagementRecord): EngagementResponse = EngagementResponse(
            articleId = record.articleId,
            pageViews = record.pageViews,
            averageReadTimeSeconds = record.averageReadTimeSeconds,
            likes = record.interactions.likes,
            shares = record.interactions.shares,
            comments = record.interactions.comments
        )
    }
}

/**
 * Response DTO representing aggregated engagement metrics for an author.
 */
data class AggregatedEngagementResponse(
    val authorId: UUID,
    val totalPageViews: Long,
    val averageReadTimeSeconds: Double,
    val totalLikes: Long,
    val totalShares: Long,
    val totalComments: Long
) {
    companion object {
        fun fromDomain(aggregated: AggregatedEngagement): AggregatedEngagementResponse =
            AggregatedEngagementResponse(
                authorId = aggregated.authorId,
                totalPageViews = aggregated.totalPageViews,
                averageReadTimeSeconds = aggregated.averageReadTimeSeconds,
                totalLikes = aggregated.totalInteractions.likes,
                totalShares = aggregated.totalInteractions.shares,
                totalComments = aggregated.totalInteractions.comments
            )
    }
}
