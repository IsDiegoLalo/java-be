package com.platform.content.domain.port

import com.platform.content.domain.model.AggregatedEngagement
import com.platform.content.domain.model.EngagementRecord
import java.util.UUID

/**
 * Port for retrieving engagement analytics (read side).
 * Separated from write operations per ISP — read path aggregates data
 * on demand without impacting write throughput.
 */
interface EngagementReadPort {
    fun getByArticleId(articleId: UUID): EngagementRecord
    fun getAggregatedByAuthorId(authorId: UUID): AggregatedEngagement
}
