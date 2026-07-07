package com.platform.content.domain.model

import java.time.Instant
import java.util.UUID

data class OutboxEvent(
    val id: UUID,
    val aggregateType: String,
    val aggregateId: UUID,
    val eventType: String,
    val payload: String,
    val status: OutboxEventStatus,
    val retryCount: Int,
    val createdAt: Instant,
    val lastAttemptedAt: Instant?
)

enum class OutboxEventStatus {
    PENDING, DELIVERED, FAILED
}
