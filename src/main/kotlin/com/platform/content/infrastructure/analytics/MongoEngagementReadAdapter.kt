package com.platform.content.infrastructure.analytics

import com.platform.content.domain.model.AggregatedEngagement
import com.platform.content.domain.model.EngagementRecord
import com.platform.content.domain.model.InteractionCounts
import com.platform.content.domain.port.ArticleFilter
import com.platform.content.domain.port.ArticleRepository
import com.platform.content.domain.port.EngagementReadPort
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.ArithmeticOperators
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * MongoDB adapter for reading engagement analytics (Adapter Pattern).
 * Implements the EngagementReadPort domain abstraction (DIP).
 *
 * For single-article queries, fetches the document directly by articleId.
 * For author-level aggregation, retrieves the author's article IDs via the
 * ArticleRepository port, then runs a MongoDB aggregation pipeline to compute
 * weighted average read time and summed metrics.
 */
@Component
class MongoEngagementReadAdapter(
    private val mongoTemplate: MongoTemplate,
    private val articleRepository: ArticleRepository
) : EngagementReadPort {

    /**
     * Retrieves the engagement record for a single article.
     * Returns zeroed metrics if no engagement data exists for the article.
     */
    override fun getByArticleId(articleId: UUID): EngagementRecord {
        val query = Query(Criteria.where("articleId").`is`(articleId.toString()))
        val document = mongoTemplate.findOne(query, EngagementDocument::class.java)

        return document?.toDomain(articleId) ?: zeroedEngagementRecord(articleId)
    }

    /**
     * Retrieves aggregated engagement metrics across all articles by the given author.
     *
     * Pipeline:
     * 1. Fetches the author's article IDs from PostgreSQL via ArticleRepository
     * 2. Matches engagement documents for those article IDs in MongoDB
     * 3. Groups to compute: sum(pageViews), weighted avg read time, sum(interactions)
     *
     * Weighted average read time = sum(averageReadTimeSeconds * pageViews) / sum(pageViews)
     * Returns zeroed metrics if no engagement records are found.
     */
    override fun getAggregatedByAuthorId(authorId: UUID): AggregatedEngagement {
        val articleIds = findArticleIdsByAuthor(authorId)

        if (articleIds.isEmpty()) {
            return zeroedAggregatedEngagement(authorId)
        }

        val articleIdStrings = articleIds.map { it.toString() }

        val aggregation = Aggregation.newAggregation(
            Aggregation.match(Criteria.where("articleId").`in`(articleIdStrings)),
            Aggregation.group()
                .sum("pageViews").`as`("totalPageViews")
                .sum(
                    ArithmeticOperators.valueOf("averageReadTimeSeconds")
                        .multiplyBy("pageViews")
                ).`as`("weightedReadTimeSum")
                .sum("pageViews").`as`("totalPageViewsForAvg")
                .sum("interactions.likes").`as`("totalLikes")
                .sum("interactions.shares").`as`("totalShares")
                .sum("interactions.comments").`as`("totalComments")
        )

        val results = mongoTemplate.aggregate(
            aggregation,
            "engagement_records",
            AggregationResult::class.java
        )

        val result = results.uniqueMappedResult
            ?: return zeroedAggregatedEngagement(authorId)

        val averageReadTime = if (result.totalPageViewsForAvg > 0) {
            result.weightedReadTimeSum / result.totalPageViewsForAvg
        } else {
            0.0
        }

        return AggregatedEngagement(
            authorId = authorId,
            totalPageViews = result.totalPageViews,
            averageReadTimeSeconds = averageReadTime,
            totalInteractions = InteractionCounts(
                likes = result.totalLikes,
                shares = result.totalShares,
                comments = result.totalComments
            )
        )
    }

    /**
     * Finds all article IDs belonging to the given author via the domain ArticleRepository port.
     */
    private fun findArticleIdsByAuthor(authorId: UUID): List<UUID> {
        val page = articleRepository.findAll(
            filter = ArticleFilter(authorId = authorId),
            pageable = Pageable.unpaged()
        )
        return page.content.map { it.id }
    }

    private fun zeroedEngagementRecord(articleId: UUID) = EngagementRecord(
        articleId = articleId,
        pageViews = 0,
        averageReadTimeSeconds = 0.0,
        interactions = InteractionCounts(likes = 0, shares = 0, comments = 0)
    )

    private fun zeroedAggregatedEngagement(authorId: UUID) = AggregatedEngagement(
        authorId = authorId,
        totalPageViews = 0,
        averageReadTimeSeconds = 0.0,
        totalInteractions = InteractionCounts(likes = 0, shares = 0, comments = 0)
    )

    private fun EngagementDocument.toDomain(articleId: UUID) = EngagementRecord(
        articleId = articleId,
        pageViews = pageViews,
        averageReadTimeSeconds = averageReadTimeSeconds,
        interactions = InteractionCounts(
            likes = interactions.likes,
            shares = interactions.shares,
            comments = interactions.comments
        )
    )
}

/**
 * Internal class mapping the MongoDB aggregation pipeline results.
 */
private data class AggregationResult(
    val totalPageViews: Long = 0,
    val weightedReadTimeSum: Double = 0.0,
    val totalPageViewsForAvg: Long = 0,
    val totalLikes: Long = 0,
    val totalShares: Long = 0,
    val totalComments: Long = 0
)
