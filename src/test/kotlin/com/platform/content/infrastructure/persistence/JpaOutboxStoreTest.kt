package com.platform.content.infrastructure.persistence

import com.platform.content.domain.model.OutboxEvent
import com.platform.content.domain.model.OutboxEventStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*

class JpaOutboxStoreTest {

    private lateinit var repository: SpringDataOutboxEventRepository
    private lateinit var store: JpaOutboxStore

    @BeforeEach
    fun setUp() {
        repository = mockk()
        store = JpaOutboxStore(repository)
    }

    @Test
    fun `save should persist and return the domain event`() {
        val event = createPendingEvent()
        val entity = OutboxEventEntity.fromDomain(event)

        every { repository.save(any<OutboxEventEntity>()) } returns entity

        val result = store.save(event)

        assertEquals(event.id, result.id)
        assertEquals(event.aggregateType, result.aggregateType)
        assertEquals(event.aggregateId, result.aggregateId)
        assertEquals(event.eventType, result.eventType)
        assertEquals(event.payload, result.payload)
        assertEquals(OutboxEventStatus.PENDING, result.status)
        verify { repository.save(any<OutboxEventEntity>()) }
    }

    @Test
    fun `findPending should return only pending events`() {
        val event = createPendingEvent()
        val entity = OutboxEventEntity.fromDomain(event)

        every { repository.findByStatus(OutboxEventStatus.PENDING) } returns listOf(entity)

        val results = store.findPending()

        assertEquals(1, results.size)
        assertEquals(event.id, results[0].id)
        assertEquals(OutboxEventStatus.PENDING, results[0].status)
    }

    @Test
    fun `findPending should return empty list when no pending events`() {
        every { repository.findByStatus(OutboxEventStatus.PENDING) } returns emptyList()

        val results = store.findPending()

        assertTrue(results.isEmpty())
    }

    @Test
    fun `markDelivered should update status and lastAttemptedAt`() {
        val event = createPendingEvent()
        val entity = OutboxEventEntity.fromDomain(event)
        val entitySlot = slot<OutboxEventEntity>()

        every { repository.findById(event.id) } returns Optional.of(entity)
        every { repository.save(capture(entitySlot)) } answers { entitySlot.captured }

        store.markDelivered(event.id)

        assertEquals(OutboxEventStatus.DELIVERED, entitySlot.captured.status)
        assertNotNull(entitySlot.captured.lastAttemptedAt)
    }

    @Test
    fun `markDelivered should throw when event not found`() {
        val id = UUID.randomUUID()
        every { repository.findById(id) } returns Optional.empty()

        assertThrows(IllegalArgumentException::class.java) {
            store.markDelivered(id)
        }
    }

    @Test
    fun `markFailed should update status, increment retryCount, and set lastAttemptedAt`() {
        val event = createPendingEvent()
        val entity = OutboxEventEntity.fromDomain(event)
        val entitySlot = slot<OutboxEventEntity>()

        every { repository.findById(event.id) } returns Optional.of(entity)
        every { repository.save(capture(entitySlot)) } answers { entitySlot.captured }

        store.markFailed(event.id, "Kafka broker unavailable")

        assertEquals(OutboxEventStatus.FAILED, entitySlot.captured.status)
        assertEquals(1, entitySlot.captured.retryCount)
        assertNotNull(entitySlot.captured.lastAttemptedAt)
    }

    @Test
    fun `markFailed should throw when event not found`() {
        val id = UUID.randomUUID()
        every { repository.findById(id) } returns Optional.empty()

        assertThrows(IllegalArgumentException::class.java) {
            store.markFailed(id, "connection timeout")
        }
    }

    private fun createPendingEvent(): OutboxEvent = OutboxEvent(
        id = UUID.randomUUID(),
        aggregateType = "Article",
        aggregateId = UUID.randomUUID(),
        eventType = "ArticlePublished",
        payload = """{"articleId":"abc-123","title":"Test"}""",
        status = OutboxEventStatus.PENDING,
        retryCount = 0,
        createdAt = Instant.now(),
        lastAttemptedAt = null
    )
}
