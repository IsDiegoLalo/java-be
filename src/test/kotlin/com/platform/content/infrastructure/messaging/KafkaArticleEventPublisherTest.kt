package com.platform.content.infrastructure.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.platform.content.domain.model.ArticlePublishedEvent
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KafkaArticleEventPublisherTest {

    private lateinit var kafkaTemplate: KafkaTemplate<String, String>
    private lateinit var outboxStore: OutboxStore
    private lateinit var objectMapper: ObjectMapper
    private lateinit var publisher: KafkaArticleEventPublisher

    @BeforeEach
    fun setUp() {
        kafkaTemplate = mockk()
        outboxStore = mockk(relaxed = true)
        objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
        publisher = KafkaArticleEventPublisher(kafkaTemplate, outboxStore, objectMapper)
    }

    private fun createTestEvent(): ArticlePublishedEvent {
        return ArticlePublishedEvent(
            articleId = UUID.randomUUID(),
            title = "Test Article",
            authorId = UUID.randomUUID(),
            category = "Technology",
            tags = listOf("kotlin", "spring"),
            publishedAt = Instant.now()
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

    @Test
    fun `should serialize event to JSON and save to outbox before Kafka send`() {
        val event = createTestEvent()
        mockSuccessfulKafkaSend()

        val outboxEventSlot = slot<OutboxEvent>()
        every { outboxStore.save(capture(outboxEventSlot)) } answers { outboxEventSlot.captured }

        publisher.publishArticlePublished(event)

        val savedOutboxEvent = outboxEventSlot.captured
        assertEquals(KafkaArticleEventPublisher.AGGREGATE_TYPE, savedOutboxEvent.aggregateType)
        assertEquals(event.articleId, savedOutboxEvent.aggregateId)
        assertEquals(KafkaArticleEventPublisher.EVENT_TYPE, savedOutboxEvent.eventType)
        assertEquals(OutboxEventStatus.PENDING, savedOutboxEvent.status)
        assertEquals(0, savedOutboxEvent.retryCount)
        assertNotNull(savedOutboxEvent.payload)

        // Verify JSON payload contains the event fields
        val payloadJson = objectMapper.readTree(savedOutboxEvent.payload)
        assertEquals(event.articleId.toString(), payloadJson.get("articleId").asText())
        assertEquals(event.title, payloadJson.get("title").asText())
        assertEquals(event.authorId.toString(), payloadJson.get("authorId").asText())
        assertEquals(event.category, payloadJson.get("category").asText())
    }

    @Test
    fun `should mark event as delivered on successful Kafka send`() {
        val event = createTestEvent()
        mockSuccessfulKafkaSend()

        val outboxEventSlot = slot<OutboxEvent>()
        every { outboxStore.save(capture(outboxEventSlot)) } answers { outboxEventSlot.captured }

        publisher.publishArticlePublished(event)

        verify(exactly = 1) { outboxStore.markDelivered(outboxEventSlot.captured.id) }
        verify(exactly = 0) { outboxStore.markFailed(any(), any()) }
    }

    @Test
    fun `should mark event as failed after all retry attempts exhausted`() {
        val event = createTestEvent()

        // Make Kafka send always fail
        val failedFuture = CompletableFuture<SendResult<String, String>>()
        failedFuture.completeExceptionally(RuntimeException("Broker unavailable"))
        every { kafkaTemplate.send(any<String>(), any<String>(), any<String>()) } returns failedFuture

        val outboxEventSlot = slot<OutboxEvent>()
        every { outboxStore.save(capture(outboxEventSlot)) } answers { outboxEventSlot.captured }

        publisher.publishArticlePublished(event)

        verify(exactly = 0) { outboxStore.markDelivered(any()) }
        verify(exactly = 1) { outboxStore.markFailed(outboxEventSlot.captured.id, any()) }
    }

    @Test
    fun `should retry up to MAX_ATTEMPTS times before marking as failed`() {
        val event = createTestEvent()

        val failedFuture = CompletableFuture<SendResult<String, String>>()
        failedFuture.completeExceptionally(RuntimeException("Broker unavailable"))
        every { kafkaTemplate.send(any<String>(), any<String>(), any<String>()) } returns failedFuture

        val outboxEventSlot = slot<OutboxEvent>()
        every { outboxStore.save(capture(outboxEventSlot)) } answers { outboxEventSlot.captured }

        publisher.publishArticlePublished(event)

        // Should attempt MAX_ATTEMPTS times
        verify(exactly = KafkaArticleEventPublisher.MAX_ATTEMPTS) {
            kafkaTemplate.send(any<String>(), any<String>(), any<String>())
        }
    }

    @Test
    fun `should succeed on second attempt after first failure`() {
        val event = createTestEvent()

        val failedFuture = CompletableFuture<SendResult<String, String>>()
        failedFuture.completeExceptionally(RuntimeException("Temporary failure"))

        val metadata = RecordMetadata(TopicPartition("article.published", 0), 0, 0, 0L, 0, 0)
        val sendResult = SendResult<String, String>(
            ProducerRecord("article.published", "key", "value"),
            metadata
        )
        val successFuture = CompletableFuture.completedFuture(sendResult)

        // First call fails, second succeeds
        every { kafkaTemplate.send(any<String>(), any<String>(), any<String>()) } returns failedFuture andThen successFuture

        val outboxEventSlot = slot<OutboxEvent>()
        every { outboxStore.save(capture(outboxEventSlot)) } answers { outboxEventSlot.captured }

        publisher.publishArticlePublished(event)

        verify(exactly = 1) { outboxStore.markDelivered(outboxEventSlot.captured.id) }
        verify(exactly = 0) { outboxStore.markFailed(any(), any()) }
    }

    @Test
    fun `should write to outbox store before attempting Kafka send`() {
        val event = createTestEvent()
        mockSuccessfulKafkaSend()

        val callOrder = mutableListOf<String>()

        every { outboxStore.save(any()) } answers {
            callOrder.add("outbox_save")
            firstArg()
        }
        every { outboxStore.markDelivered(any()) } answers {
            callOrder.add("outbox_markDelivered")
        }

        publisher.publishArticlePublished(event)

        assertTrue(callOrder.indexOf("outbox_save") < callOrder.indexOf("outbox_markDelivered"))
    }

    @Test
    fun `should send to correct Kafka topic with articleId as key`() {
        val event = createTestEvent()

        val outboxEventSlot = slot<OutboxEvent>()
        every { outboxStore.save(capture(outboxEventSlot)) } answers { outboxEventSlot.captured }

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

        publisher.publishArticlePublished(event)

        assertEquals(KafkaArticleEventPublisher.TOPIC, topicSlot.captured)
        assertEquals(event.articleId.toString(), keySlot.captured)
    }

    @Test
    fun `should include failure reason when marking event as failed`() {
        val event = createTestEvent()
        val errorMessage = "Connection refused"

        val failedFuture = CompletableFuture<SendResult<String, String>>()
        failedFuture.completeExceptionally(RuntimeException(errorMessage))
        every { kafkaTemplate.send(any<String>(), any<String>(), any<String>()) } returns failedFuture

        val outboxEventSlot = slot<OutboxEvent>()
        every { outboxStore.save(capture(outboxEventSlot)) } answers { outboxEventSlot.captured }

        val reasonSlot = slot<String>()
        every { outboxStore.markFailed(any(), capture(reasonSlot)) } returns Unit

        publisher.publishArticlePublished(event)

        assertTrue(reasonSlot.captured.contains(errorMessage))
    }
}
