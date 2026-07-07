package com.platform.content.infrastructure.messaging

import com.platform.content.domain.model.OutboxEvent
import com.platform.content.domain.model.OutboxEventStatus
import com.platform.content.domain.port.OutboxStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OutboxProcessorTest {

    private lateinit var outboxStore: OutboxStore
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>
    private lateinit var processor: OutboxProcessor

    @BeforeEach
    fun setUp() {
        outboxStore = mockk(relaxed = true)
        kafkaTemplate = mockk()
        processor = OutboxProcessor(outboxStore, kafkaTemplate)
    }

    private fun createPendingEvent(
        retryCount: Int = 0,
        aggregateId: UUID = UUID.randomUUID()
    ): OutboxEvent {
        return OutboxEvent(
            id = UUID.randomUUID(),
            aggregateType = "Article",
            aggregateId = aggregateId,
            eventType = "ArticlePublished",
            payload = """{"articleId":"$aggregateId","title":"Test"}""",
            status = OutboxEventStatus.PENDING,
            retryCount = retryCount,
            createdAt = Instant.now(),
            lastAttemptedAt = null
        )
    }

    private fun mockSuccessfulKafkaSend() {
        val metadata = RecordMetadata(TopicPartition("article.published", 0), 0, 0, 0L, 0, 0)
        val sendResult = SendResult<String, String>(
            ProducerRecord("article.published", "key", "value"),
            metadata
        )
        val future = CompletableFuture.completedFuture(sendResult)
        every { kafkaTemplate.send(any<String>(), any<String>(), any<String>()) } returns future
    }

    private fun mockFailedKafkaSend(errorMessage: String = "Broker unavailable") {
        val failedFuture = CompletableFuture<SendResult<String, String>>()
        failedFuture.completeExceptionally(RuntimeException(errorMessage))
        every { kafkaTemplate.send(any<String>(), any<String>(), any<String>()) } returns failedFuture
    }

    @Test
    fun `should do nothing when no pending events exist`() {
        every { outboxStore.findPending() } returns emptyList()

        processor.processPendingEvents()

        verify(exactly = 1) { outboxStore.findPending() }
        verify(exactly = 0) { kafkaTemplate.send(any<String>(), any<String>(), any<String>()) }
        verify(exactly = 0) { outboxStore.markDelivered(any()) }
        verify(exactly = 0) { outboxStore.markFailed(any(), any()) }
    }

    @Test
    fun `should mark event as delivered on successful Kafka send`() {
        val event = createPendingEvent()
        every { outboxStore.findPending() } returns listOf(event)
        mockSuccessfulKafkaSend()

        processor.processPendingEvents()

        verify(exactly = 1) { outboxStore.markDelivered(event.id) }
        verify(exactly = 0) { outboxStore.markFailed(any(), any()) }
    }

    @Test
    fun `should send to correct topic with aggregateId as key`() {
        val aggregateId = UUID.randomUUID()
        val event = createPendingEvent(aggregateId = aggregateId)
        every { outboxStore.findPending() } returns listOf(event)

        val topicSlot = slot<String>()
        val keySlot = slot<String>()
        val valueSlot = slot<String>()

        val metadata = RecordMetadata(TopicPartition("article.published", 0), 0, 0, 0L, 0, 0)
        val sendResult = SendResult<String, String>(
            ProducerRecord("article.published", "key", "value"),
            metadata
        )
        val future = CompletableFuture.completedFuture(sendResult)
        every { kafkaTemplate.send(capture(topicSlot), capture(keySlot), capture(valueSlot)) } returns future

        processor.processPendingEvents()

        assertEquals("article.published", topicSlot.captured)
        assertEquals(aggregateId.toString(), keySlot.captured)
        assertEquals(event.payload, valueSlot.captured)
    }

    @Test
    fun `should mark event as failed when Kafka send fails`() {
        val event = createPendingEvent()
        every { outboxStore.findPending() } returns listOf(event)
        mockFailedKafkaSend("Connection refused")

        processor.processPendingEvents()

        verify(exactly = 0) { outboxStore.markDelivered(any()) }
        verify(exactly = 1) { outboxStore.markFailed(event.id, any()) }
    }

    @Test
    fun `should include error reason when marking event as failed`() {
        val event = createPendingEvent()
        every { outboxStore.findPending() } returns listOf(event)
        mockFailedKafkaSend("Connection refused")

        val reasonSlot = slot<String>()
        every { outboxStore.markFailed(any(), capture(reasonSlot)) } returns Unit

        processor.processPendingEvents()

        assertTrue(reasonSlot.captured.contains("Connection refused"))
    }

    @Test
    fun `should mark event as failed when retry count exceeds maximum`() {
        val event = createPendingEvent(retryCount = OutboxProcessor.MAX_RETRY_COUNT)
        every { outboxStore.findPending() } returns listOf(event)

        processor.processPendingEvents()

        verify(exactly = 0) { kafkaTemplate.send(any<String>(), any<String>(), any<String>()) }
        verify(exactly = 0) { outboxStore.markDelivered(any()) }
        verify(exactly = 1) { outboxStore.markFailed(event.id, any()) }
    }

    @Test
    fun `should not attempt Kafka send when retry count equals maximum`() {
        val event = createPendingEvent(retryCount = OutboxProcessor.MAX_RETRY_COUNT)
        every { outboxStore.findPending() } returns listOf(event)

        processor.processPendingEvents()

        verify(exactly = 0) { kafkaTemplate.send(any<String>(), any<String>(), any<String>()) }
    }

    @Test
    fun `should process multiple pending events independently`() {
        val event1 = createPendingEvent()
        val event2 = createPendingEvent()
        every { outboxStore.findPending() } returns listOf(event1, event2)
        mockSuccessfulKafkaSend()

        processor.processPendingEvents()

        verify(exactly = 1) { outboxStore.markDelivered(event1.id) }
        verify(exactly = 1) { outboxStore.markDelivered(event2.id) }
    }

    @Test
    fun `should continue processing remaining events when one fails`() {
        val event1 = createPendingEvent()
        val event2 = createPendingEvent()
        every { outboxStore.findPending() } returns listOf(event1, event2)

        val failedFuture = CompletableFuture<SendResult<String, String>>()
        failedFuture.completeExceptionally(RuntimeException("Broker unavailable"))

        val metadata = RecordMetadata(TopicPartition("article.published", 0), 0, 0, 0L, 0, 0)
        val sendResult = SendResult<String, String>(
            ProducerRecord("article.published", "key", "value"),
            metadata
        )
        val successFuture = CompletableFuture.completedFuture(sendResult)

        // First event fails, second succeeds
        every { kafkaTemplate.send(any<String>(), any<String>(), any<String>()) } returns failedFuture andThen successFuture

        processor.processPendingEvents()

        verify(exactly = 1) { outboxStore.markFailed(event1.id, any()) }
        verify(exactly = 1) { outboxStore.markDelivered(event2.id) }
    }

    @Test
    fun `should attempt Kafka send for event with retry count below maximum`() {
        val event = createPendingEvent(retryCount = OutboxProcessor.MAX_RETRY_COUNT - 1)
        every { outboxStore.findPending() } returns listOf(event)
        mockSuccessfulKafkaSend()

        processor.processPendingEvents()

        verify(exactly = 1) { kafkaTemplate.send(any<String>(), any<String>(), any<String>()) }
        verify(exactly = 1) { outboxStore.markDelivered(event.id) }
    }
}
