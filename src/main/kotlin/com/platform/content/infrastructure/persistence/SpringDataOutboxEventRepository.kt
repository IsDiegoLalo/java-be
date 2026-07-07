package com.platform.content.infrastructure.persistence

import com.platform.content.domain.model.OutboxEventStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Spring Data JPA interface for OutboxEventEntity persistence.
 * Used internally by JpaOutboxStore adapter.
 */
interface SpringDataOutboxEventRepository : JpaRepository<OutboxEventEntity, UUID> {
    fun findByStatus(status: OutboxEventStatus): List<OutboxEventEntity>
}
