package com.platform.content.infrastructure.analytics

import com.platform.content.domain.model.InteractionType
import com.platform.content.domain.port.EngagementWritePort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/**
 * MongoDB adapter implementing the engagement write port.
 * Uses MongoTemplate for atomic upsert operations with $inc and $set operators,
 * ensuring thread-safe concurrent metric recording without read-modify-write races.
 */
@Component
class MongoEngagementWriteAdapter(
    private val mongoTemplate: MongoTemplate
) : EngagementWritePort {

    /**
     * Records a page view by atomically incrementing the pageViews counter.
     * Creates the document if it doesn't exist (upsert).
     */
    override fun recordPageView(articleId: UUID) {
        val query = Query(Criteria.where("articleId").`is`(articleId.toString()))
        val update = Update()
            .inc("pageViews", 1)
            .set("lastUpdated", Instant.now())

        mongoTemplate.upsert(query, update, EngagementDocument::class.java)
    }

    /**
     * Records read time by incrementing totalReadTimeSeconds and readTimeCount,
     * then recalculating the running average.
     *
     * Uses a two-step atomic approach:
     * 1. Increment totals with $inc
     * 2. Read updated totals and recalculate average with $set
     *
     * Note: The average recalculation is eventually consistent under extreme concurrency,
     * but acceptable for analytics use cases.
     */
    override fun recordReadTime(articleId: UUID, seconds: Int) {
        // Discard invalid read time values per requirement 6.7
        if (seconds < 1 || seconds > 3600) {
            return
        }

        val query = Query(Criteria.where("articleId").`is`(articleId.toString()))

        // Step 1: Atomically increment totalReadTimeSeconds and readTimeCount
        val incrementUpdate = Update()
            .inc("totalReadTimeSeconds", seconds.toLong())
            .inc("readTimeCount", 1)
            .set("lastUpdated", Instant.now())

        mongoTemplate.upsert(query, incrementUpdate, EngagementDocument::class.java)

        // Step 2: Read updated document and recalculate average
        val document = mongoTemplate.findOne(query, EngagementDocument::class.java)
        if (document != null && document.readTimeCount > 0) {
            val newAverage = document.totalReadTimeSeconds.toDouble() / document.readTimeCount.toDouble()
            val averageUpdate = Update().set("averageReadTimeSeconds", newAverage)
            mongoTemplate.updateFirst(query, averageUpdate, EngagementDocument::class.java)
        }
    }

    /**
     * Records an interaction event by atomically incrementing the appropriate
     * interaction counter (likes, shares, or comments) based on the interaction type.
     */
    override fun recordInteraction(articleId: UUID, type: InteractionType) {
        val query = Query(Criteria.where("articleId").`is`(articleId.toString()))
        val fieldName = when (type) {
            InteractionType.LIKE -> "interactions.likes"
            InteractionType.SHARE -> "interactions.shares"
            InteractionType.COMMENT -> "interactions.comments"
        }

        val update = Update()
            .inc(fieldName, 1)
            .set("lastUpdated", Instant.now())

        mongoTemplate.upsert(query, update, EngagementDocument::class.java)
    }
}
