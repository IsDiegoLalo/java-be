package com.platform.content.infrastructure.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.platform.content.domain.model.ArticlePublishedEvent
import com.platform.content.domain.model.OutboxEvent
import com.platform.content.domain.model.OutboxEventStatus
import com.platform.content.domain.port.ArticleEventPublisher
import com.platform.content.domain.port.OutboxStore
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/**
 * Kafka-based implementation of [ArticleEventPublisher] using the Outbox Pattern.
 *
 * Flow:
 * 1. Serialize the event to JSON
 * 2. Persist the event in the outbox store (guarantees durability)
 * 3. Attempt to send to Kafka with exponential backoff retry (1s, 2s, 4s)
 * 4. On success: mark event as DELIVERED in outbox
 * 5. On final failure: log at ERROR level, mark event as FAILED in outbox
 */
@Component
class KafkaArticleEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val outboxStore: OutboxStore,
    private val objectMapper: ObjectMapper
) : ArticleEventPublisher {

    private val logger = LoggerFactory.getLogger(KafkaArticleEventPublisher::class.java)

    companion object {
        const val TOPIC = "article.published"
        const val AGGREGATE_TYPE = "Article"
        const val EVENT_TYPE = "ArticlePublished"
        const val MAX_ATTEMPTS = 3
        private val BACKOFF_DELAYS_MS = longArrayOf(1000L, 2000L, 4000L)
    }

    override fun publishArticlePublished(event: ArticlePublishedEvent) {
        val payload = serializeEvent(event)

        val outboxEvent = OutboxEvent(
            id = UUID.randomUUID(),
            aggregateType = AGGREGATE_TYPE,
            aggregateId = event.articleId,
            eventType = EVENT_TYPE,
            payload = payload,
            status = OutboxEventStatus.PENDING,
            retryCount = 0,
            createdAt = Instant.now(),
            lastAttemptedAt = null
        )

        // Write to outbox first (durability guarantee)
        val savedEvent = outboxStore.save(outboxEvent)

        // Attempt Kafka delivery with exponential backoff
        attemptKafkaDelivery(savedEvent, payload, event.articleId)
    }

    /**
     * Attempts to send the event to Kafka with exponential backoff retry.
     * Retry delays: 1s, 2s, 4s (3 attempts max).
     * On final failure: logs at ERROR and marks event as FAILED in the outbox store.
     */
    private fun attemptKafkaDelivery(outboxEvent: OutboxEvent, payload: String, articleId: UUID) {
        var lastException: Exception? = null

        for (attempt in 0 until MAX_ATTEMPTS) {
            try {
                if (attempt > 0) {
                    val delayMs = BACKOFF_DELAYS_MS[attempt - 1]
                    logger.debug("Retrying Kafka send for article {} (attempt {}, backoff {}ms)", articleId, attempt + 1, delayMs)
                    Thread.sleep(delayMs)
                }

                val future = kafkaTemplate.send(TOPIC, articleId.toString(), payload)
                future.get() // Block until send completes or fails

                // Success - mark as delivered
                outboxStore.markDelivered(outboxEvent.id)
                logger.info("Successfully published ArticlePublishedEvent for article {}", articleId)
                return
            } catch (e: Exception) {
                lastException = e
                logger.warn(
                    "Kafka send attempt {} of {} failed for article {}: {}",
                    attempt + 1, MAX_ATTEMPTS, articleId, e.message
                )
            }
        }

        // All retry attempts exhausted - mark as failed
        val reason = lastException?.message ?: "Unknown error after $MAX_ATTEMPTS attempts"
        outboxStore.markFailed(outboxEvent.id, reason)
        logger.error(
            "Failed to publish ArticlePublishedEvent for article {} after {} attempts. Reason: {}",
            articleId, MAX_ATTEMPTS, reason, lastException
        )
    }

    private fun serializeEvent(event: ArticlePublishedEvent): String {
        return objectMapper.writeValueAsString(event)
    }
}
