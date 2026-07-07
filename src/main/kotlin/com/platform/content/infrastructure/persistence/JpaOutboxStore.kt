package com.platform.content.infrastructure.persistence

import com.platform.content.domain.model.OutboxEvent
import com.platform.content.domain.model.OutboxEventStatus
import com.platform.content.domain.port.OutboxStore
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * JPA adapter implementing the OutboxStore domain port.
 * Persists outbox events to PostgreSQL for reliable event delivery.
 */
@Repository
@Transactional
class JpaOutboxStore(
    private val repository: SpringDataOutboxEventRepository
) : OutboxStore {

    override fun save(event: OutboxEvent): OutboxEvent {
        val entity = OutboxEventEntity.fromDomain(event)
        return repository.save(entity).toDomain()
    }

    @Transactional(readOnly = true)
    override fun findPending(): List<OutboxEvent> {
        return repository.findByStatus(OutboxEventStatus.PENDING)
            .map { it.toDomain() }
    }

    override fun markDelivered(id: UUID) {
        val entity = repository.findById(id).orElseThrow {
            IllegalArgumentException("Outbox event with id $id not found")
        }
        entity.status = OutboxEventStatus.DELIVERED
        entity.lastAttemptedAt = Instant.now()
        repository.save(entity)
    }

    override fun markFailed(id: UUID, reason: String) {
        val entity = repository.findById(id).orElseThrow {
            IllegalArgumentException("Outbox event with id $id not found")
        }
        entity.status = OutboxEventStatus.FAILED
        entity.retryCount = entity.retryCount + 1
        entity.lastAttemptedAt = Instant.now()
        repository.save(entity)
    }
}
