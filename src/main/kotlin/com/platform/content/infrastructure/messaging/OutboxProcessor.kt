package com.platform.content.infrastructure.messaging

import com.platform.content.domain.model.OutboxEvent
import com.platform.content.domain.port.OutboxStore
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Scheduled processor that retries delivery of pending outbox events to Kafka.
 *
 * Runs every 30 seconds to pick up events that were not delivered during the
 * initial publish attempt. Events are retried up to [MAX_RETRY_COUNT] times
 * before being marked as permanently failed.
 *
 * This ensures at-least-once delivery semantics as required by the outbox pattern.
 */
@Component
class OutboxProcessor(
    private val outboxStore: OutboxStore,
    private val kafkaTemplate: KafkaTemplate<String, String>
) {

    private val logger = LoggerFactory.getLogger(OutboxProcessor::class.java)

    companion object {
        const val MAX_RETRY_COUNT = 5
        private const val TOPIC = "article.published"
    }

    /**
     * Processes pending outbox events every 30 seconds.
     * For each pending event, attempts to send to Kafka.
     * On success: marks as delivered. On failure: marks as failed if max retries exceeded.
     */
    @Scheduled(fixedRate = 30000)
    fun processPendingEvents() {
        val pendingEvents = outboxStore.findPending()

        if (pendingEvents.isEmpty()) {
            return
        }

        logger.info("Processing {} pending outbox events", pendingEvents.size)

        for (event in pendingEvents) {
            processEvent(event)
        }
    }

    private fun processEvent(event: OutboxEvent) {
        if (event.retryCount >= MAX_RETRY_COUNT) {
            val reason = "Max retry count ($MAX_RETRY_COUNT) exceeded"
            outboxStore.markFailed(event.id, reason)
            logger.error(
                "Outbox event {} for aggregate {} permanently failed: {}",
                event.id, event.aggregateId, reason
            )
            return
        }

        try {
            val future = kafkaTemplate.send(TOPIC, event.aggregateId.toString(), event.payload)
            future.get()

            outboxStore.markDelivered(event.id)
            logger.info(
                "Successfully delivered outbox event {} for aggregate {} (retry #{})",
                event.id, event.aggregateId, event.retryCount
            )
        } catch (e: Exception) {
            val reason = e.message ?: "Unknown error"
            outboxStore.markFailed(event.id, reason)
            logger.warn(
                "Failed to deliver outbox event {} for aggregate {} (retry #{}): {}",
                event.id, event.aggregateId, event.retryCount, reason
            )
        }
    }
}
