package com.platform.content.domain.port

import com.platform.content.domain.model.ArticlePublishedEvent

/**
 * Port for publishing domain events when articles are published.
 * Implementation uses Kafka with outbox pattern for at-least-once delivery.
 */
interface ArticleEventPublisher {
    fun publishArticlePublished(event: ArticlePublishedEvent)
}
