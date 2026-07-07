package com.platform.content.infrastructure.persistence

import com.platform.content.domain.model.OutboxEvent
import com.platform.content.domain.model.OutboxEventStatus
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * JPA entity mapping for the outbox_events table.
 * Stores domain events pending delivery to Kafka.
 * Payload is stored as JSONB in PostgreSQL.
 */
@Entity
@Table(name = "outbox_events")
class OutboxEventEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "aggregate_type", nullable = false, length = 50)
    var aggregateType: String = "",

    @Column(name = "aggregate_id", nullable = false)
    var aggregateId: UUID = UUID.randomUUID(),

    @Column(name = "event_type", nullable = false, length = 50)
    var eventType: String = "",

    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    var payload: String = "",

    @Column(name = "status", nullable = false, length = 20)
    @Convert(converter = OutboxEventStatusConverter::class)
    var status: OutboxEventStatus = OutboxEventStatus.PENDING,

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "last_attempted_at")
    var lastAttemptedAt: Instant? = null
) {
    /**
     * Converts this JPA entity to the domain model.
     */
    fun toDomain(): OutboxEvent = OutboxEvent(
        id = id,
        aggregateType = aggregateType,
        aggregateId = aggregateId,
        eventType = eventType,
        payload = payload,
        status = status,
        retryCount = retryCount,
        createdAt = createdAt,
        lastAttemptedAt = lastAttemptedAt
    )

    companion object {
        /**
         * Creates a JPA entity from the domain model.
         */
        fun fromDomain(event: OutboxEvent): OutboxEventEntity = OutboxEventEntity(
            id = event.id,
            aggregateType = event.aggregateType,
            aggregateId = event.aggregateId,
            eventType = event.eventType,
            payload = event.payload,
            status = event.status,
            retryCount = event.retryCount,
            createdAt = event.createdAt,
            lastAttemptedAt = event.lastAttemptedAt
        )
    }
}
