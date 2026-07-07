package com.platform.content.domain.port

import com.platform.content.domain.model.InteractionType
import java.util.UUID

/**
 * Port for recording engagement analytics (write side).
 * Separated from read operations per ISP — write path is high-throughput
 * and handled asynchronously via MongoDB.
 */
interface EngagementWritePort {
    fun recordPageView(articleId: UUID)
    fun recordReadTime(articleId: UUID, seconds: Int)
    fun recordInteraction(articleId: UUID, type: InteractionType)
}
