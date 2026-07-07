package com.platform.content.infrastructure.analytics

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * MongoDB document representing engagement metrics for a single article.
 * Uses atomic update operations for high-write throughput (CQRS-lite pattern).
 */
@Document(collection = "engagement_records")
data class EngagementDocument(
    @Id
    val id: String? = null,

    @Indexed(unique = true)
    val articleId: String = "",

    val pageViews: Long = 0,

    val totalReadTimeSeconds: Long = 0,

    val readTimeCount: Long = 0,

    val averageReadTimeSeconds: Double = 0.0,

    val interactions: InteractionSubDocument = InteractionSubDocument(),

    @Indexed
    val lastUpdated: Instant = Instant.now()
)

/**
 * Embedded sub-document for interaction counts within an engagement record.
 */
data class InteractionSubDocument(
    val likes: Long = 0,
    val shares: Long = 0,
    val comments: Long = 0
)
