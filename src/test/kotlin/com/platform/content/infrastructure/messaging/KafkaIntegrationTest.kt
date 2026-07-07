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
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Integration tests for Kafka event publishing using Testcontainers.
 * Verifies end-to-end delivery of ArticlePublishedEvent to a real Kafka broker,
 * outbox status management, and retry/failure behavior.
 *
 * Uses a mocked OutboxStore to isolate Kafka integration from PostgreSQL concerns.
 *
 * Requirements validated: 8.1, 8.3, 8.4, 8.5
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KafkaIntegrationTest {

    companion object {
        private const val TOPIC = "article.published"

        @Container
        @JvmStatic
        val kafka: KafkaContainer = KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0")
        )
    }

    private lateinit var kafkaTemplate: KafkaTemplate<String, String>
    private lateinit var outboxStore: OutboxStore
    private lateinit var objectMapper: ObjectMapper
    private lateinit var publisher: KafkaArticleEventPublisher
    private lateinit var consumer: KafkaConsumer<String, String>

    @BeforeAll
    fun setUpAll() {
        objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    }

    @BeforeEach
    fun setUp() {
        // Create a real KafkaTemplate connected to the Testcontainers Kafka broker
        val producerProps = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java.name,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java.name,
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.RETRIES_CONFIG to 0
        )

        val producerFactory = DefaultKafkaProducerFactory<String, String>(producerProps)
        kafkaTemplate = KafkaTemplate(producerFactory)

        // Mock OutboxStore to verify outbox interactions without needing PostgreSQL
        outboxStore = mockk(relaxed = true)

        publisher = KafkaArticleEventPublisher(kafkaTemplate, outboxStore, objectMapper)

        // Create a consumer to verify messages arrive on the topic
        val consumerProps = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to "test-group-${UUID.randomUUID()}",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java.name,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java.name
        )

        consumer = KafkaConsumer(consumerProps)
        consumer.subscribe(listOf(TOPIC))
    }

    @AfterEach
    fun tearDown() {
        consumer.close()
    }

    private fun createTestEvent(
        articleId: UUID = UUID.randomUUID(),
        title: String = "Integration Test Article",
        authorId: UUID = UUID.randomUUID(),
        category: String = "Technology",
        tags: List<String> = listOf("kafka", "integration"),
        publishedAt: Instant = Instant.now()
    ): ArticlePublishedEvent {
        return ArticlePublishedEvent(
            articleId = articleId,
            title = title,
            authorId = authorId,
            category = category,
            tags = tags,
            publishedAt = publishedAt
        )
    }

    // ===========================
    // Successful Delivery (Req 8.1)
    // ===========================

    @Nested
    @DisplayName("Successful Event Delivery")
    inner class SuccessfulDeliveryTests {

        @Test
        @DisplayName("should deliver event to Kafka topic and verify payload on consumer")
        fun `publish event and consume from topic`() {
            val event = createTestEvent()

            val outboxEventSlot = slot<OutboxEvent>()
            every { outboxStore.save(capture(outboxEventSlot)) } answers { outboxEventSlot.captured }

            // Publish the event via the publisher
            publisher.publishArticlePublished(event)

            // Consume from the topic and verify the payload
            val records = consumer.poll(Duration.ofSeconds(10))

            assertTrue(records.count() > 0, "Should have received at least one message from Kafka")

            val record = records.first()
            assertEquals(event.articleId.toString(), record.key())

            // Verify the payload contains all required fields
            val payloadNode = objectMapper.readTree(record.value())
            assertEquals(event.articleId.toString(), payloadNode.get("articleId").asText())
            assertEquals(event.title, payloadNode.get("title").asText())
            assertEquals(event.authorId.toString(), payloadNode.get("authorId").asText())
            assertEquals(event.category, payloadNode.get("category").asText())
            assertEquals(event.tags.size, payloadNode.get("tags").size())
            event.tags.forEachIndexed { index, tag ->
                assertEquals(tag, payloadNode.get("tags").get(index).asText())
            }
            assertNotNull(payloadNode.get("publishedAt"))
        }

        @Test
        @DisplayName("should mark outbox event as DELIVERED after successful Kafka send")
        fun `outbox event marked delivered on success`() {
            val event = createTestEvent()

            val outboxEventSlot = slot<OutboxEvent>()
            every { outboxStore.save(capture(outboxEventSlot)) } answers { outboxEventSlot.captured }

            publisher.publishArticlePublished(event)

            // Verify outbox was marked as delivered
            verify(exactly = 1) { outboxStore.markDelivered(outboxEventSlot.captured.id) }
            verify(exactly = 0) { outboxStore.markFailed(any(), any()) }
        }

        @Test
        @DisplayName("should save event to outbox with PENDING status before Kafka delivery")
        fun `outbox event saved as PENDING before delivery`() {
            val event = createTestEvent()

            val outboxEventSlot = slot<OutboxEvent>()
            every { outboxStore.save(capture(outboxEventSlot)) } answers { outboxEventSlot.captured }

            publisher.publishArticlePublished(event)

            val savedEvent = outboxEventSlot.captured
            assertEquals(OutboxEventStatus.PENDING, savedEvent.status)
            assertEquals(0, savedEvent.retryCount)
            assertEquals("Article", savedEvent.aggregateType)
            assertEquals("ArticlePublished", savedEvent.eventType)
            assertEquals(event.articleId, savedEvent.aggregateId)
            assertNotNull(savedEvent.createdAt)
        }

        @Test
        @DisplayName("should include all required fields in event payload")
        fun `event payload contains all required fields`() {
            val event = createTestEvent(
                title = "Complete Payload Test",
                category = "Science",
                tags = listOf("test", "payload", "complete")
            )

            val outboxEventSlot = slot<OutboxEvent>()
            every { outboxStore.save(capture(outboxEventSlot)) } answers { outboxEventSlot.captured }

            publisher.publishArticlePublished(event)

            // Consume and verify all required fields are present and non-null
            val records = consumer.poll(Duration.ofSeconds(10))
            assertTrue(records.count() > 0)

            val payload = objectMapper.readTree(records.first().value())
            assertAll(
                { assertFalse(payload.get("articleId").isNull, "articleId should not be null") },
                { assertFalse(payload.get("title").isNull, "title should not be null") },
                { assertFalse(payload.get("authorId").isNull, "authorId should not be null") },
                { assertFalse(payload.get("category").isNull, "category should not be null") },
                { assertTrue(payload.get("tags").isArray, "tags should be an array") },
                { assertFalse(payload.get("publishedAt").isNull, "publishedAt should not be null") }
            )
        }
    }

    // ===========================
    // Retry Behavior (Req 8.3)
    // ===========================

    @Nested
    @DisplayName("Retry and Failure Behavior")
    inner class RetryBehaviorTests {

        @Test
        @DisplayName("should mark event as FAILED in outbox when Kafka broker is unreachable")
        fun `marks event failed when broker unavailable`() {
            // Create a publisher with an invalid bootstrap server to simulate broker unavailability
            val badProducerProps = mapOf(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:1",
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java.name,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java.name,
                ProducerConfig.ACKS_CONFIG to "all",
                ProducerConfig.RETRIES_CONFIG to 0,
                ProducerConfig.MAX_BLOCK_MS_CONFIG to 1000,
                ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG to 1000,
                ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG to 2000
            )

            val badProducerFactory = DefaultKafkaProducerFactory<String, String>(badProducerProps)
            val badKafkaTemplate = KafkaTemplate(badProducerFactory)

            val failOutboxStore: OutboxStore = mockk(relaxed = true)
            val failPublisher = KafkaArticleEventPublisher(badKafkaTemplate, failOutboxStore, objectMapper)

            val event = createTestEvent()

            val outboxEventSlot = slot<OutboxEvent>()
            every { failOutboxStore.save(capture(outboxEventSlot)) } answers { outboxEventSlot.captured }

            failPublisher.publishArticlePublished(event)

            // Verify the event was marked as failed (dead-letter store fallback)
            verify(exactly = 1) { failOutboxStore.markFailed(outboxEventSlot.captured.id, any()) }
            verify(exactly = 0) { failOutboxStore.markDelivered(any()) }
        }

        @Test
        @DisplayName("should persist event in outbox even when Kafka delivery fails")
        fun `outbox event persisted regardless of kafka failure`() {
            val badProducerProps = mapOf(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:1",
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java.name,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java.name,
                ProducerConfig.MAX_BLOCK_MS_CONFIG to 1000,
                ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG to 1000,
                ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG to 2000
            )

            val badProducerFactory = DefaultKafkaProducerFactory<String, String>(badProducerProps)
            val badKafkaTemplate = KafkaTemplate(badProducerFactory)

            val failOutboxStore: OutboxStore = mockk(relaxed = true)
            val failPublisher = KafkaArticleEventPublisher(badKafkaTemplate, failOutboxStore, objectMapper)

            val event = createTestEvent()

            val outboxEventSlot = slot<OutboxEvent>()
            every { failOutboxStore.save(capture(outboxEventSlot)) } answers { outboxEventSlot.captured }

            failPublisher.publishArticlePublished(event)

            // Outbox save should always be called (at-least-once delivery guarantee)
            verify(exactly = 1) { failOutboxStore.save(any()) }

            val savedEvent = outboxEventSlot.captured
            assertEquals(OutboxEventStatus.PENDING, savedEvent.status)
            assertEquals(event.articleId, savedEvent.aggregateId)
        }
    }

    // ===========================
    // Dead-Letter Store Fallback (Req 8.4, 8.5)
    // ===========================

    @Nested
    @DisplayName("Dead-Letter Store Fallback")
    inner class DeadLetterStoreTests {

        @Test
        @DisplayName("should guarantee event is either delivered or stored in outbox (at-least-once)")
        fun `at least once delivery guarantee`() {
            val event = createTestEvent()

            val outboxEventSlot = slot<OutboxEvent>()
            every { outboxStore.save(capture(outboxEventSlot)) } answers { outboxEventSlot.captured }

            publisher.publishArticlePublished(event)

            // Event was saved to outbox first (durability guarantee)
            verify(exactly = 1) { outboxStore.save(any()) }

            // And since we have a working broker, it was marked delivered
            verify(exactly = 1) { outboxStore.markDelivered(outboxEventSlot.captured.id) }
        }

        @Test
        @DisplayName("should include failure reason in markFailed call for dead-letter tracking")
        fun `failure reason included in dead letter store`() {
            val badProducerProps = mapOf(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:1",
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java.name,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java.name,
                ProducerConfig.MAX_BLOCK_MS_CONFIG to 1000,
                ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG to 1000,
                ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG to 2000
            )

            val badProducerFactory = DefaultKafkaProducerFactory<String, String>(badProducerProps)
            val badKafkaTemplate = KafkaTemplate(badProducerFactory)

            val failOutboxStore: OutboxStore = mockk(relaxed = true)
            val failPublisher = KafkaArticleEventPublisher(badKafkaTemplate, failOutboxStore, objectMapper)

            val event = createTestEvent()

            val outboxEventSlot = slot<OutboxEvent>()
            every { failOutboxStore.save(capture(outboxEventSlot)) } answers { outboxEventSlot.captured }

            val reasonSlot = slot<String>()
            every { failOutboxStore.markFailed(any(), capture(reasonSlot)) } returns Unit

            failPublisher.publishArticlePublished(event)

            // Verify a non-empty reason was recorded
            assertTrue(reasonSlot.captured.isNotBlank(), "Failure reason should be non-blank")
        }
    }
}
