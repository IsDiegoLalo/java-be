package com.platform.content.application.analytics

import com.platform.content.domain.EntityNotFoundException
import com.platform.content.domain.model.AggregatedEngagement
import com.platform.content.domain.model.EngagementRecord
import com.platform.content.domain.model.InteractionType
import com.platform.content.domain.port.ArticleRepository
import com.platform.content.domain.port.EngagementReadPort
import com.platform.content.domain.port.EngagementWritePort
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Application service orchestrating engagement analytics use cases (SRP).
 *
 * Write operations (page views, read time, interactions) are processed asynchronously
 * via Spring's @Async to ensure the main article API responses remain fast (requirement 6.5).
 *
 * Read operations (single article, author aggregation) are synchronous and delegate
 * to the EngagementReadPort abstraction (DIP).
 *
 * Events referencing non-existent articles are discarded with a warning log (requirement 6.6).
 * Invalid read time values (outside 1–3600) are discarded without recording (requirement 6.7).
 */
@Service
class EngagementService(
    private val engagementWritePort: EngagementWritePort,
    private val engagementReadPort: EngagementReadPort,
    private val articleRepository: ArticleRepository
) {

    private val logger = LoggerFactory.getLogger(EngagementService::class.java)

    /**
     * Records a page view event for the specified article.
     * Discards the event if the article does not exist.
     */
    @Async
    fun recordPageView(articleId: UUID) {
        if (!articleExists(articleId)) {
            logger.warn("Discarding page view event for non-existent article: {}", articleId)
            return
        }
        engagementWritePort.recordPageView(articleId)
    }

    /**
     * Records a read time metric for the specified article.
     * Discards the event if the article does not exist or if the read time
     * is outside the valid range of 1–3600 seconds.
     */
    @Async
    fun recordReadTime(articleId: UUID, seconds: Int) {
        if (!articleExists(articleId)) {
            logger.warn("Discarding read time event for non-existent article: {}", articleId)
            return
        }
        if (seconds < 1 || seconds > 3600) {
            logger.warn(
                "Discarding invalid read time value {} for article: {}. Valid range is 1-3600 seconds.",
                seconds,
                articleId
            )
            return
        }
        engagementWritePort.recordReadTime(articleId, seconds)
    }

    /**
     * Records an interaction event (like, share, or comment) for the specified article.
     * Discards the event if the article does not exist.
     */
    @Async
    fun recordInteraction(articleId: UUID, type: InteractionType) {
        if (!articleExists(articleId)) {
            logger.warn("Discarding interaction event for non-existent article: {}", articleId)
            return
        }
        engagementWritePort.recordInteraction(articleId, type)
    }

    /**
     * Retrieves engagement metrics for a single article.
     * Throws EntityNotFoundException if the article does not exist (requirement 7.4).
     * Returns zeroed metrics if no engagement has been recorded (requirement 7.3).
     */
    fun getArticleEngagement(articleId: UUID): EngagementRecord {
        if (!articleExists(articleId)) {
            throw EntityNotFoundException("Article", articleId)
        }
        return engagementReadPort.getByArticleId(articleId)
    }

    /**
     * Retrieves aggregated engagement metrics across all articles by the given author.
     * Returns zeroed metrics if the author has no articles (requirement 7.5).
     */
    fun getAuthorEngagement(authorId: UUID): AggregatedEngagement {
        return engagementReadPort.getAggregatedByAuthorId(authorId)
    }

    private fun articleExists(articleId: UUID): Boolean {
        return articleRepository.findById(articleId) != null
    }
}
