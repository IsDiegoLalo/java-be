package com.platform.content.domain.port

import com.platform.content.domain.model.OutboxEvent
import java.util.UUID

/**
 * Port for the transactional outbox pattern.
 * Events are persisted before publishing to guarantee at-least-once delivery.
 * Failed events are retained for reprocessing.
 */
interface OutboxStore {
    fun save(event: OutboxEvent): OutboxEvent
    fun findPending(): List<OutboxEvent>
    fun markDelivered(id: UUID)
    fun markFailed(id: UUID, reason: String)
}
